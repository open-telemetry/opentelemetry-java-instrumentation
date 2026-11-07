/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Shared event bus tests. The base class is compiled against Vert.x 3.0, so it only touches APIs
 * that are binary compatible up to Vert.x 5; anything version-specific (lifecycle, request/reply)
 * is delegated to the subclasses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractVertxEventBusTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-eventbus-3.0";
  private static final AttributeKey<List<String>> CAPTURED_HEADER =
      stringArrayKey("messaging.header.Test-Message-Header");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected Vertx vertx;

  protected abstract Vertx createVertx() throws Exception;

  protected abstract void closeVertx(Vertx vertx) throws Exception;

  /** Sends a request to {@code address} and passes the reply to {@code onReply}. */
  protected abstract void request(
      EventBus eventBus, String address, Object body, Consumer<Message<Object>> onReply);

  /** Replies to {@code message} and runs {@code onReply} once the reply to that reply arrives. */
  protected abstract void replyAndRequest(Message<?> message, Object body, Runnable onReply);

  /** {@link MessageProducer#write} changed its return type between Vert.x versions. */
  protected abstract void write(MessageProducer<Object> producer, Object body);

  @BeforeAll
  void setUp() throws Exception {
    vertx = createVertx();
  }

  @AfterAll
  void tearDown() throws Exception {
    if (vertx != null) {
      closeVertx(vertx);
    }
  }

  @Test
  void sendAndReceiveWithDeliveryOptions() throws InterruptedException {
    String address = "address.send." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(1);
    eventBus.consumer(
        address,
        message -> {
          testing.runWithSpan("handler-inner", () -> {});
          received.countDown();
        });

    testing.runWithSpan("parent", () -> eventBus.send(address, "hello", new DeliveryOptions()));

    assertThat(received.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace -> assertProducerConsumerTrace(trace, "parent", "send", address));
    assertProducerMetrics("send", address);
    assertConsumerMetrics(address, null);
  }

  @Test
  void publishWithoutDeliveryOptions() throws InterruptedException {
    String address = "address.publish." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(1);
    eventBus.consumer(
        address,
        message -> {
          testing.runWithSpan("handler-inner", () -> {});
          received.countDown();
        });

    testing.runWithSpan("parent", () -> eventBus.publish(address, "hello"));

    assertThat(received.await(10, SECONDS)).isTrue();

    // Even without an explicit DeliveryOptions, context must propagate — the agent
    // injects W3C trace headers directly into the MessageImpl's headers MultiMap
    // via EventBusImpl.createMessage, so the consumer joins the same trace.
    testing.waitAndAssertTraces(
        trace -> assertProducerConsumerTrace(trace, "parent", "publish", address));
    assertProducerMetrics("publish", address);
    assertConsumerMetrics(address, null);
  }

  @Test
  void requestReply() throws InterruptedException {
    String address = "address.request." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    eventBus.consumer(
        address,
        message -> {
          testing.runWithSpan("handler-inner", () -> {});
          message.reply("pong");
        });

    CountDownLatch replied = new CountDownLatch(1);
    testing.runWithSpan(
        "parent", () -> request(eventBus, address, "ping", reply -> replied.countDown()));

    assertThat(replied.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace -> assertProducerConsumerTrace(trace, "parent", "request", address));
    assertProducerMetrics("request", address);
    assertConsumerMetrics(address, null);
  }

  @Test
  void replyWithReplyHandler() throws InterruptedException {
    String address = "address.reply." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch acknowledged = new CountDownLatch(1);
    eventBus.consumer(
        address,
        message -> {
          testing.runWithSpan("handler-inner", () -> {});
          replyAndRequest(message, "pong", acknowledged::countDown);
        });

    testing.runWithSpan("parent", () -> request(eventBus, address, "ping", r -> r.reply("ack")));

    assertThat(acknowledged.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace -> assertProducerConsumerTrace(trace, "parent", "request", address));
  }

  @Test
  void sender() throws InterruptedException {
    String address = "address.sender." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(1);
    eventBus.consumer(address, message -> received.countDown());

    MessageProducer<Object> producer = eventBus.sender(address);
    testing.runWithSpan("parent", () -> write(producer, "hello"));

    assertThat(received.await(10, SECONDS)).isTrue();

    // The context must propagate to the consumer. Early Vert.x 3 producers go through
    // EventBus.send, so a producer span may be present. The credit messages later versions exchange
    // internally to flow control the producer must not be traced.
    testing.waitAndAssertTraces(
        trace ->
            trace.satisfies(
                spans ->
                    assertThat(spans)
                        .extracting(SpanData::getName)
                        .contains("parent", "process " + address)
                        .isSubsetOf("parent", "send " + address, "process " + address)));
  }

  @Test
  void reusedDeliveryOptions() throws InterruptedException {
    String address = "address.reuse." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(2);
    eventBus.consumer(
        address,
        message -> {
          testing.runWithSpan("handler-inner", () -> {});
          received.countDown();
        });

    // Each consumer must join its own producer's trace when a populated DeliveryOptions is reused.
    DeliveryOptions options = new DeliveryOptions().addHeader("application-header", "value");
    testing.runWithSpan("parent1", () -> eventBus.send(address, "first", options));
    testing.runWithSpan("parent2", () -> eventBus.send(address, "second", options));

    assertThat(received.await(10, SECONDS)).isTrue();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent1", "parent2"),
        trace -> assertProducerConsumerTrace(trace, "parent1", "send", address),
        trace -> assertProducerConsumerTrace(trace, "parent2", "send", address));
  }

  @Test
  void multipleBaggageHeaders() throws InterruptedException {
    String address = "address.baggage." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<Baggage> baggage = new AtomicReference<>();
    eventBus.consumer(
        address,
        message -> {
          baggage.set(Baggage.current());
          testing.runWithSpan("handler-inner", () -> {});
          received.countDown();
        });

    DeliveryOptions options =
        new DeliveryOptions().addHeader("baggage", "first=1").addHeader("baggage", "second=2");
    testing.runWithSpan("parent", () -> eventBus.send(address, "hello", options));

    assertThat(received.await(10, SECONDS)).isTrue();

    assertThat(baggage.get().getEntryValue("first")).isEqualTo("1");
    assertThat(baggage.get().getEntryValue("second")).isEqualTo("2");

    testing.waitAndAssertTraces(
        trace -> assertProducerConsumerTrace(trace, "parent", "send", address));
  }

  @Test
  void reusedDeliveryOptionsDoNotLeakBaggage() throws InterruptedException {
    String address = "address.reuse.baggage." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(2);
    Map<Object, Baggage> baggage = new ConcurrentHashMap<>();
    Map<Object, String> applicationHeaders = new ConcurrentHashMap<>();
    eventBus.consumer(
        address,
        message -> {
          baggage.put(message.body(), Baggage.current());
          applicationHeaders.put(message.body(), message.headers().get("application-header"));
          testing.runWithSpan("handler-inner", () -> {});
          received.countDown();
        });

    // the propagator writes nothing for the second send, because its baggage is empty
    DeliveryOptions options = new DeliveryOptions().addHeader("application-header", "value");
    try (Scope ignored = Baggage.builder().put("tenant", "A").build().makeCurrent()) {
      testing.runWithSpan("parent1", () -> eventBus.send(address, "first", options));
    }
    testing.runWithSpan("parent2", () -> eventBus.send(address, "second", options));

    assertThat(received.await(10, SECONDS)).isTrue();

    assertThat(baggage.get("first").getEntryValue("tenant")).isEqualTo("A");
    assertThat(baggage.get("second").getEntryValue("tenant")).isNull();
    assertThat(applicationHeaders).containsEntry("first", "value").containsEntry("second", "value");
    // the application's options must not be changed by the instrumentation
    assertThat(options.getHeaders().names()).containsExactly("application-header");

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent1", "parent2"),
        trace -> assertProducerConsumerTrace(trace, "parent1", "send", address),
        trace -> assertProducerConsumerTrace(trace, "parent2", "send", address));
  }

  @Test
  void handlerThrows() throws InterruptedException {
    String address = "address.error." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();
    IllegalStateException error = new IllegalStateException("boom");

    CountDownLatch received = new CountDownLatch(1);
    eventBus.consumer(
        address,
        message -> {
          received.countDown();
          throw error;
        });

    testing.runWithSpan("parent", () -> eventBus.send(address, "hello"));

    assertThat(received.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> assertProducer(span, "send", address).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("process " + address)
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("messaging.system.name"), "vertx"),
                            equalTo(MESSAGING_DESTINATION_NAME, address),
                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                            equalTo(MESSAGING_OPERATION_TYPE, "process"),
                            equalTo(ERROR_TYPE, IllegalStateException.class.getName()))
                        .hasStatusSatisfying(status -> status.hasCode(StatusCode.ERROR))
                        .hasException(error)));
    assertProducerMetrics("send", address);
    assertConsumerMetrics(address, IllegalStateException.class.getName());
  }

  @Test
  void captureHeaders() throws InterruptedException {
    String address = "address.headers." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(1);
    eventBus.consumer(address, message -> received.countDown());

    DeliveryOptions options = new DeliveryOptions().addHeader("Test-Message-Header", "test");
    testing.runWithSpan("parent", () -> eventBus.send(address, "hello", options));

    assertThat(received.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("send " + address)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("messaging.system.name"), "vertx"),
                            equalTo(MESSAGING_DESTINATION_NAME, address),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(CAPTURED_HEADER, singletonList("test"))),
                span ->
                    span.hasName("process " + address)
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("messaging.system.name"), "vertx"),
                            equalTo(MESSAGING_DESTINATION_NAME, address),
                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                            equalTo(MESSAGING_OPERATION_TYPE, "process"),
                            equalTo(CAPTURED_HEADER, singletonList("test")))));
  }

  /**
   * Sends from {@link #vertx} to a consumer that is only registered on {@code otherNode}, so the
   * message and its trace headers have to cross the cluster transport to reach the consumer.
   */
  protected void assertPropagatesToOtherNode(
      Vertx otherNode, Consumer<MessageConsumer<Object>> awaitRegistration)
      throws InterruptedException {
    String address = "address.cluster." + System.nanoTime();

    CountDownLatch received = new CountDownLatch(1);
    MessageConsumer<Object> consumer =
        otherNode
            .eventBus()
            .consumer(
                address,
                message -> {
                  testing.runWithSpan("handler-inner", () -> {});
                  received.countDown();
                });
    awaitRegistration.accept(consumer);

    testing.runWithSpan("parent", () -> vertx.eventBus().send(address, "hello"));

    assertThat(received.await(10, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace -> assertProducerConsumerTrace(trace, "parent", "send", address));
  }

  private static void assertProducerConsumerTrace(
      TraceAssert trace, String parentName, String operationName, String address) {
    trace.hasSpansSatisfyingExactly(
        span -> span.hasName(parentName).hasNoParent(),
        span -> assertProducer(span, operationName, address).hasParent(trace.getSpan(0)),
        span -> assertConsumer(span, address).hasParent(trace.getSpan(1)),
        span -> span.hasName("handler-inner").hasParent(trace.getSpan(2)));
  }

  private static SpanDataAssert assertProducer(
      SpanDataAssert span, String operationName, String address) {
    return span.hasName(operationName + " " + address)
        .hasKind(PRODUCER)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "vertx"),
            equalTo(MESSAGING_DESTINATION_NAME, address),
            equalTo(MESSAGING_OPERATION_NAME, operationName),
            equalTo(MESSAGING_OPERATION_TYPE, "send"));
  }

  private static void assertProducerMetrics(String operationName, String address) {
    Attributes attributes = metricAttributes(operationName, address, null);
    assertDurationMetric(
        "messaging.client.operation.duration",
        address,
        attributes.toBuilder().put(MESSAGING_OPERATION_TYPE, "send").build());
    assertCounterMetric("messaging.client.sent.messages", address, attributes);
  }

  private static void assertConsumerMetrics(String address, @Nullable String errorType) {
    Attributes attributes = metricAttributes("process", address, errorType);
    assertDurationMetric("messaging.process.duration", address, attributes);
    assertCounterMetric("messaging.client.consumed.messages", address, attributes);
  }

  private static Attributes metricAttributes(
      String operationName, String address, @Nullable String errorType) {
    AttributesBuilder attributes =
        Attributes.builder()
            .put(stringKey("messaging.system.name"), "vertx")
            .put(MESSAGING_DESTINATION_NAME, address)
            .put(MESSAGING_OPERATION_NAME, operationName);
    if (errorType != null) {
      attributes.put(ERROR_TYPE, errorType);
    }
    return attributes.build();
  }

  // metrics accumulate across tests, so only the points of this test's address are looked at
  private static void assertDurationMetric(
      String metricName, String address, Attributes attributes) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .filteredOn(
                                point ->
                                    address.equals(
                                        point.getAttributes().get(MESSAGING_DESTINATION_NAME)))
                            .singleElement()
                            .satisfies(
                                point -> {
                                  assertThat(point.getCount()).isEqualTo(1);
                                  assertThat(point.getSum()).isGreaterThan(0);
                                  assertThat(point.getAttributes()).isEqualTo(attributes);
                                })));
  }

  private static void assertCounterMetric(
      String metricName, String address, Attributes attributes) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getLongSumData().getPoints())
                            .filteredOn(
                                point ->
                                    address.equals(
                                        point.getAttributes().get(MESSAGING_DESTINATION_NAME)))
                            .singleElement()
                            .satisfies(
                                point -> {
                                  assertThat(point.getValue()).isEqualTo(1);
                                  assertThat(point.getAttributes()).isEqualTo(attributes);
                                })));
  }

  private static SpanDataAssert assertConsumer(SpanDataAssert span, String address) {
    return span.hasName("process " + address)
        .hasKind(CONSUMER)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "vertx"),
            equalTo(MESSAGING_DESTINATION_NAME, address),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"));
  }
}
