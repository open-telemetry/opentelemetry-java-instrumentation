/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.java.impl.ClientImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RocketMqSimpleConsumerTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-5.0";
  private static final String TOPIC = "normal-topic-0";
  private static final String TAG = "tagA";
  private static final String CONSUMER_GROUP = "simple-consumer-group";
  private static final String EMPTY_CONSUMER_GROUP = "empty-simple-consumer-group";
  private static final String[] KEYS = {"simple-key-0", "simple-key-1"};
  private static final byte[] BODY = "simple-consumer".getBytes(UTF_8);
  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      Boolean.getBoolean("otel.instrumentation.messaging.experimental.receive-telemetry.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ClientServiceProvider provider = ClientServiceProvider.loadService();
  private final RocketMqProxyContainer container = new RocketMqProxyContainer();
  private SimpleConsumer consumer;
  private Producer producer;

  @BeforeAll
  void setUp() throws ClientException {
    container.start();
    cleanup.deferAfterAll(container::close);
    ClientConfiguration clientConfiguration =
        ClientConfiguration.newBuilder()
            .setEndpoints(container.endpoints)
            .setRequestTimeout(Duration.ofSeconds(10))
            .build();
    Map<String, FilterExpression> subscriptionExpressions = new HashMap<>();
    subscriptionExpressions.put(TOPIC, new FilterExpression(TAG, FilterExpressionType.TAG));
    consumer =
        provider
            .newSimpleConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(CONSUMER_GROUP)
            .setAwaitDuration(Duration.ofSeconds(5))
            .setSubscriptionExpressions(subscriptionExpressions)
            .build();
    cleanup.deferAfterAll(() -> ((ClientImpl) consumer).stopAsync());
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(TOPIC)
            .build();
    cleanup.deferAfterAll(producer);
  }

  @Test
  void shouldInstrumentSynchronousReceive() throws ClientException {
    assumeTrue(emitStableMessagingSemconv());
    SpanData sendSpan = sendMessage();

    testing.runWithSpan(
        "sync receive parent",
        () -> {
          List<MessageView> messages = consumer.receive(1, Duration.ofSeconds(10));
          for (MessageView message : messages) {
            consumer.ack(message);
          }
        });

    assertSuccessfulReceiveTrace("sync receive parent", sendSpan);
  }

  @Test
  void shouldInstrumentAsynchronousReceive() throws ClientException {
    assumeTrue(emitStableMessagingSemconv());
    SpanData sendSpan = sendMessage();

    List<MessageView> messages =
        testing.runWithSpan(
            "async receive parent", () -> consumer.receiveAsync(1, Duration.ofSeconds(10)).join());
    for (MessageView message : messages) {
      consumer.ack(message);
    }

    assertSuccessfulReceiveTrace("async receive parent", sendSpan);
  }

  @Test
  void shouldNotInstrumentEmptySynchronousAndAsynchronousReceive() throws ClientException {
    assumeTrue(emitStableMessagingSemconv());

    Map<String, FilterExpression> subscriptionExpressions = new HashMap<>();
    subscriptionExpressions.put(
        TOPIC, new FilterExpression("missing-tag", FilterExpressionType.TAG));
    SimpleConsumer emptyConsumer =
        provider
            .newSimpleConsumerBuilder()
            .setClientConfiguration(
                ClientConfiguration.newBuilder()
                    .setEndpoints(container.endpoints)
                    .setRequestTimeout(Duration.ofSeconds(10))
                    .build())
            .setConsumerGroup(EMPTY_CONSUMER_GROUP)
            .setAwaitDuration(Duration.ofSeconds(1))
            .setSubscriptionExpressions(subscriptionExpressions)
            .build();
    cleanup.deferCleanup(() -> ((ClientImpl) emptyConsumer).stopAsync());

    List<MessageView> syncMessages =
        testing.runWithSpan(
            "sync empty parent", () -> emptyConsumer.receive(1, Duration.ofSeconds(1)));
    List<MessageView> asyncMessages =
        testing.runWithSpan(
            "async empty parent",
            () -> emptyConsumer.receiveAsync(1, Duration.ofSeconds(1)).join());

    assertThat(syncMessages).isEmpty();
    assertThat(asyncMessages).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("sync empty parent").hasKind(SpanKind.INTERNAL).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("async empty parent").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void shouldInstrumentReceiveWhenReceiveTelemetryDisabled() throws ClientException {
    assumeTrue(emitStableMessagingSemconv());
    assumeFalse(RECEIVE_TELEMETRY_ENABLED);
    SpanData sendSpan = sendMessage();

    testing.runWithSpan(
        "disabled receive parent",
        () -> {
          List<MessageView> messages = consumer.receive(1, Duration.ofSeconds(10));
          assertThat(messages).hasSize(1);
          consumer.ack(messages.get(0));
        });

    assertSuccessfulReceiveTrace("disabled receive parent", sendSpan);
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                equalTo(MESSAGING_DESTINATION_NAME, TOPIC),
                                                equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                                equalTo(MESSAGING_OPERATION_TYPE, "receive"))))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                equalTo(MESSAGING_DESTINATION_NAME, TOPIC),
                                                equalTo(MESSAGING_OPERATION_NAME, "receive"))))));
  }

  @Test
  void shouldPreserveLegacySuccessfulReceive() throws ClientException {
    assumeFalse(emitStableMessagingSemconv());
    sendMessage();

    testing.runWithSpan(
        "legacy receive parent",
        () -> {
          List<MessageView> messages = consumer.receive(1, Duration.ofSeconds(10));
          for (MessageView message : messages) {
            testing.runWithSpan("legacy process child", () -> {});
            consumer.ack(message);
          }
        });

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.CONSUMER, SpanKind.INTERNAL),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(TOPIC + " receive").hasKind(SpanKind.CONSUMER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("legacy receive parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("legacy process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldInstrumentSynchronousReceiveErrorOnlyInStableMode() {
    assertThatThrownBy(
            () ->
                testing.runWithSpan(
                    "sync error parent", () -> consumer.receive(0, Duration.ofSeconds(1))))
        .isInstanceOf(IllegalArgumentException.class);

    assertReceiveErrorTrace("sync error parent");
  }

  @Test
  void shouldInstrumentAsynchronousReceiveErrorOnlyInStableMode() {
    assertThatThrownBy(
            () ->
                testing.runWithSpan(
                    "async error parent",
                    () -> consumer.receiveAsync(0, Duration.ofSeconds(1)).join()))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);

    assertReceiveErrorTrace("async error parent");
  }

  private SpanData sendMessage() throws ClientException {
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(TOPIC)
            .setTag(TAG)
            .setKeys(KEYS)
            .setBody(BODY)
            .build();
    SendReceipt receipt = producer.send(message);
    AtomicReference<SpanData> sendSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasKind(SpanKind.PRODUCER)
                      .hasName(emitStableMessagingSemconv() ? "send " + TOPIC : TOPIC + " publish")
                      .hasAttribute(MESSAGING_MESSAGE_ID, receipt.getMessageId().toString());
                  sendSpan.set(span.actual());
                }));
    testing.clearData();
    return sendSpan.get();
  }

  private static void assertSuccessfulReceiveTrace(String parentName, SpanData sendSpan) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(parentName).hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertReceiveSpan(span, sendSpan).hasParent(trace.getSpan(0))));
  }

  private static void assertReceiveErrorTrace(String parentName) {
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName(parentName).hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      span.hasName("receive")
                          .hasKind(SpanKind.CLIENT)
                          .hasStatus(StatusData.error())
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                              equalTo(MESSAGING_SYSTEM, "rocketmq"),
                              equalTo(MESSAGING_OPERATION_NAME, "receive"),
                              equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                              equalTo(ERROR_TYPE, IllegalArgumentException.class.getName()))));
      return;
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(parentName).hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  private static SpanDataAssert assertReceiveSpan(SpanDataAssert span, SpanData sendSpan) {
    return span.hasKind(SpanKind.CLIENT)
        .hasName("receive " + TOPIC)
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
            equalTo(MESSAGING_SYSTEM, "rocketmq"),
            equalTo(MESSAGING_ROCKETMQ_NAMESPACE, ""),
            equalTo(MESSAGING_DESTINATION_NAME, TOPIC),
            equalTo(MESSAGING_OPERATION_NAME, "receive"),
            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
            equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, TAG),
            equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(KEYS)))
        .hasLinks(
            LinkData.create(
                sendSpan.getSpanContext(),
                Attributes.of(
                    MESSAGING_MESSAGE_ID, sendSpan.getAttributes().get(MESSAGING_MESSAGE_ID))));
  }
}
