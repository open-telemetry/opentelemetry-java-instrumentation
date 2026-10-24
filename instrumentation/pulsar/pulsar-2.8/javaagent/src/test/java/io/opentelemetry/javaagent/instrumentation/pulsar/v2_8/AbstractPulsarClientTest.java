/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.BatchMessageContainer;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.interceptor.ProducerInterceptor;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.ProducerImpl;
import org.apache.pulsar.client.impl.TypedMessageBuilderImpl;
import org.apache.pulsar.common.naming.TopicName;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.pulsar.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractPulsarClientTest {
  private static final boolean EXPERIMENTAL_ATTRIBUTES_ENABLED =
      Boolean.getBoolean("otel.instrumentation.pulsar.experimental-span-attributes");

  static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES_ENABLED ? value : null;
  }

  private static final Logger logger = LoggerFactory.getLogger(AbstractPulsarClientTest.class);

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.pulsar-2.8";

  // Pulsar 2.8 transaction clients lack the coordinator-connect handshake required by newer
  // brokers. The v5 API requires a 5.x broker.
  private static final DockerImageName DEFAULT_IMAGE_NAME =
      DockerImageName.parse(
          Integer.parseInt(PulsarVersion.getVersion().split("\\.")[0]) >= 5
              ? "apachepulsar/pulsar:5.0.0"
              : "apachepulsar/pulsar:2.8.0");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static PulsarContainer pulsar;
  static PulsarClient client;
  static PulsarAdmin admin;
  static Producer<String> producer;
  static Consumer<String> consumer;
  static Producer<String> producer2;

  static String brokerHost;
  static int brokerPort;

  static final double[] DURATION_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  @BeforeAll
  static void beforeAll() throws PulsarClientException {
    pulsar =
        new PulsarContainer(DEFAULT_IMAGE_NAME)
            .withEnv("PULSAR_MEM", "-Xmx128m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withTransactions();
    pulsar.start();

    brokerHost = pulsar.getHost();
    brokerPort = pulsar.getMappedPort(6650);
    client =
        PulsarClient.builder()
            .serviceUrl(pulsar.getPulsarBrokerUrl())
            .enableTransaction(true)
            .build();
    admin = PulsarAdmin.builder().serviceHttpUrl(pulsar.getHttpServiceUrl()).build();
  }

  @AfterEach
  void afterEach() throws PulsarClientException {
    if (producer != null) {
      producer.close();
    }
    if (consumer != null) {
      consumer.close();
    }
    if (producer2 != null) {
      producer2.close();
    }
  }

  @AfterAll
  static void afterAll() throws PulsarClientException {
    if (client != null) {
      client.close();
    }
    if (admin != null) {
      admin.close();
    }
    pulsar.close();
  }

  @Test
  void producerCallbackRestoresCompletionThread() throws Exception {
    String topic = "persistent://public/default/producerCallbackRestoresCompletionThread";
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(true)
            .batchingMaxPublishDelay(1, HOURS)
            .sendTimeout(0, SECONDS)
            .create();
    ContextKey<String> key = ContextKey.named("pulsar-completion-context");
    Context caller = Context.current().with(key, "caller");
    List<CompletableFuture<Void>> continuations = new ArrayList<>();
    try (Scope ignored = caller.makeCurrent()) {
      for (int i = 0; i < 2; i++) {
        int completedSends = i + 1;
        continuations.add(
            producer
                .sendAsync("message")
                .handle(
                    (id, error) -> {
                      assertThat(Context.current().get(key)).isEqualTo("caller");
                      assertThat(Span.current().getSpanContext())
                          .isEqualTo(Span.fromContext(caller).getSpanContext());
                      assertThat(error).isInstanceOf(PulsarClientException.class);
                      assertThat(
                              testing.spans().stream()
                                  .filter(span -> span.getKind() == SpanKind.PRODUCER)
                                  .count())
                          .isEqualTo(completedSends);
                      return null;
                    }));
      }
    }
    // Drive the real linked callback on a thread with an unrelated current context.
    Field field = ProducerImpl.class.getDeclaredField("batchMessageContainer");
    field.setAccessible(true);
    BatchMessageContainer batch = (BatchMessageContainer) field.get(producer);
    testing.runWithSpan(
        "completion-thread",
        () -> {
          Context completionContext = Context.current().with(key, "completion");
          try (Scope ignored = completionContext.makeCurrent()) {
            batch.discard(new PulsarClientException("send failed"));
            assertThat(Context.current().get(key)).isEqualTo("completion");
            assertThat(Span.current().getSpanContext())
                .isEqualTo(Span.fromContext(completionContext).getSpanContext());
          }
        });
    CompletableFuture.allOf(continuations.toArray(new CompletableFuture<?>[0])).get(1, MINUTES);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.PRODUCER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.PRODUCER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("completion-thread").hasNoParent()));
  }

  @Test
  @SuppressWarnings("rawtypes")
  void inlineSendFailureRestoresCaller() throws Exception {
    String topic = "persistent://public/default/inlineSendFailureRestoresCaller";
    ContextKey<String> key = ContextKey.named("pulsar-inline-caller");
    AtomicReference<SpanContext> preparation = new AtomicReference<>();
    AtomicReference<SpanContext> acknowledgement = new AtomicReference<>();
    AtomicReference<String> terminalValue = new AtomicReference<>();
    AtomicReference<Thread> terminalThread = new AtomicReference<>();
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(false)
            .intercept(
                new ProducerInterceptor() {
                  @Override
                  public void close() {}

                  @Override
                  public boolean eligible(Message message) {
                    return true;
                  }

                  @Override
                  public Message beforeSend(Producer producer, Message message) {
                    preparation.set(Span.current().getSpanContext());
                    return message;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {
                    acknowledgement.set(Span.current().getSpanContext());
                    terminalValue.set(Context.current().get(key));
                    terminalThread.set(Thread.currentThread());
                  }
                })
            .create();
    producer.close();
    Context caller = Context.current().with(key, "caller");
    try (Scope ignored = caller.makeCurrent()) {
      CompletableFuture<MessageId> future = producer.sendAsync("closed");
      assertThat(preparation.get().isValid()).isTrue();
      assertThat(acknowledgement.get()).isEqualTo(preparation.get());
      assertThat(terminalValue.get()).isEqualTo("caller");
      assertThat(terminalThread.get()).isSameAs(Thread.currentThread());
      assertThat(Context.current().get(key)).isEqualTo("caller");
      assertThat(Span.current().getSpanContext())
          .isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThatThrownBy(future::join).hasCauseInstanceOf(PulsarClientException.class);
      future
          .handle(
              (id, error) -> {
                assertThat(Context.current().get(key)).isEqualTo("caller");
                assertThat(Span.current().getSpanContext())
                    .isEqualTo(Span.fromContext(caller).getSpanContext());
                assertThat(testing.spans()).hasSize(1);
                return null;
              })
          .join();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + topic).hasKind(SpanKind.PRODUCER).hasNoParent()));
  }

  @ParameterizedTest
  @SuppressWarnings({"rawtypes", "unchecked"})
  @CsvSource({
    "false,false,false",
    "false,false,true",
    "false,true,false",
    "false,true,true",
    "true,false,false",
    "true,false,true",
    "true,true,false",
    "true,true,true"
  })
  void sendContinuationsUseCaller(boolean batching, boolean failure, boolean withParent)
      throws Exception {
    String topic =
        "persistent://public/default/sendContinuations-"
            + batching
            + "-"
            + failure
            + "-"
            + withParent;
    ContextKey<String> key = ContextKey.named("pulsar-send-caller");
    CountDownLatch acknowledged = new CountDownLatch(1);
    CountDownLatch complete = new CountDownLatch(1);
    AtomicReference<Context> caller = new AtomicReference<>();
    List<SpanContext> preparations = new ArrayList<>();
    List<SpanContext> acknowledgements = new ArrayList<>();
    List<String> preparationValues = new ArrayList<>();
    List<Message<?>> replacements = new ArrayList<>();
    List<CompletableFuture<Void>> continuations = new ArrayList<>();
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(batching || failure)
            .batchingMaxPublishDelay(1, HOURS)
            .intercept(
                new ProducerInterceptor() {
                  @Override
                  public void close() {}

                  @Override
                  public boolean eligible(Message message) {
                    return true;
                  }

                  @Override
                  public Message beforeSend(Producer producer, Message message) {
                    preparations.add(Span.current().getSpanContext());
                    preparationValues.add(Context.current().get(key));
                    Message<?> replacement =
                        ((TypedMessageBuilderImpl<?>)
                                producer
                                    .newMessage()
                                    .value(message.getValue())
                                    .properties(message.getProperties()))
                            .getMessage();
                    replacements.add(replacement);
                    return replacement;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {
                    acknowledgements.add(Span.current().getSpanContext());
                    acknowledged.countDown();
                    try {
                      assertThat(complete.await(1, MINUTES)).isTrue();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      throw new AssertionError(e);
                    }
                  }
                })
            .create();
    Runnable send =
        () -> {
          caller.set(
              Baggage.builder()
                  .put("pulsar-test", "caller")
                  .build()
                  .storeInContext(Context.current().with(key, "caller")));
          try (Scope ignored = caller.get().makeCurrent()) {
            for (int i = 0; i < (batching ? 2 : 1); i++) {
              CompletableFuture<MessageId> future =
                  producer
                      .newMessage()
                      .value("message-" + i)
                      .property(
                          "traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                      .property("baggage", "pulsar-test=stale")
                      .sendAsync();
              SpanContext producerContext = preparations.get(i);
              Message<?> replacement = replacements.get(i);
              assertThat(future.isDone()).isFalse();
              continuations.add(
                  future.handle(
                      (id, error) -> {
                        assertThat(Context.current().get(key)).isEqualTo("caller");
                        assertThat(Span.current().getSpanContext())
                            .isEqualTo(Span.fromContext(caller.get()).getSpanContext());
                        assertThat(error != null).isEqualTo(failure);
                        assertThat(Baggage.current().getEntryValue("pulsar-test"))
                            .isEqualTo("caller");
                        assertThat(
                                ((MessageImpl<?>) replacement)
                                    .getMessageBuilder().getPropertiesList().stream()
                                        .filter(property -> property.getKey().equals("traceparent"))
                                        .map(property -> property.getValue()))
                            .containsExactly(
                                String.format(
                                    "00-%s-%s-%s",
                                    producerContext.getTraceId(),
                                    producerContext.getSpanId(),
                                    producerContext.getTraceFlags().asHex()));
                        assertThat(
                                ((MessageImpl<?>) replacement)
                                    .getMessageBuilder().getPropertiesList().stream()
                                        .filter(property -> property.getKey().equals("baggage"))
                                        .map(property -> property.getValue()))
                            .containsExactly("pulsar-test=caller");
                        assertThat(
                                testing.spans().stream()
                                    .filter(span -> span.getKind() == SpanKind.PRODUCER)
                                    .map(span -> span.getSpanContext().getSpanId()))
                            .contains(producerContext.getSpanId());
                        testing.runWithSpan("continuation", () -> {});
                        return null;
                      }));
            }
          }
        };
    try {
      if (withParent) {
        testing.runWithSpan("parent", send::run);
      } else {
        send.run();
      }
      assertThat(preparationValues).containsOnly("caller");
      assertThat(preparations)
          .allSatisfy(spanContext -> assertThat(spanContext.isValid()).isTrue());
      if (failure) {
        admin.topics().terminateTopic(topic);
      }
      if (batching || failure) {
        producer.flushAsync();
      }
      assertThat(acknowledged.await(1, MINUTES)).isTrue();
    } finally {
      complete.countDown();
    }
    CompletableFuture.allOf(continuations.toArray(new CompletableFuture<?>[0])).get(1, MINUTES);
    assertThat(acknowledgements)
        .extracting(SpanContext::getSpanId)
        .containsOnly(preparations.get(0).getSpanId());
    if (withParent) {
      testing.waitAndAssertTraces(
          trace -> {
            if (batching) {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasKind(SpanKind.PRODUCER).hasParent(trace.getSpan(0)),
                  span -> span.hasKind(SpanKind.PRODUCER).hasParent(trace.getSpan(0)),
                  span -> span.hasName("continuation").hasParent(trace.getSpan(0)),
                  span -> span.hasName("continuation").hasParent(trace.getSpan(0)));
            } else {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasKind(SpanKind.PRODUCER).hasParent(trace.getSpan(0)),
                  span -> span.hasName("continuation").hasParent(trace.getSpan(0)));
            }
          });
    } else if (batching) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SpanKind.PRODUCER).hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SpanKind.PRODUCER).hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(span -> span.hasName("continuation").hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(span -> span.hasName("continuation").hasNoParent()));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SpanKind.PRODUCER).hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(span -> span.hasName("continuation").hasNoParent()));
    }
  }

  @Test
  void testConsumeNonPartitionedTopicUsingBatchReceive() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopicCallBatchReceive";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    testing.runWithSpan(
        "receive-parent",
        () -> {
          Messages<String> receivedMsg = consumer.batchReceive();
          consumer.acknowledge(receivedMsg);
        });
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("send " + topic)
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("receive " + topic)
                        .hasKind(CLIENT)
                        .hasLinks(batchLink(producerSpan.get(), msgId.toString()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            batchReceiveAttributes(topic, null, false))));
  }

  @Test
  void testConsumeNonPartitionedTopicUsingBatchReceiveAsync() throws Exception {
    String topic =
        "persistent://public/default/testConsumeNonPartitionedTopicCallBatchReceiveAsync";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    CompletableFuture<Messages<String>> result =
        testing.runWithSpan(
            "receive-parent",
            () ->
                consumer
                    .batchReceiveAsync()
                    .whenComplete(
                        (messages, throwable) -> {
                          if (messages != null) {
                            testing.runWithSpan(
                                "callback", () -> acknowledgeMessages(consumer, messages));
                          }
                        }));

    assertThat(result.get(1, MINUTES)).hasSize(1);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("send " + topic)
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("receive " + topic)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(batchLink(producerSpan.get(), msgId.toString()))
                        .hasAttributesSatisfyingExactly(batchReceiveAttributes(topic, null, false)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void failedReceivePreservesParent() throws Exception {
    String topic = "persistent://public/default/failedReceivePreservesParent";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client.newConsumer(Schema.STRING).subscriptionName("test_sub").topic(topic).subscribe();

    CompletableFuture<Message<String>> receive =
        testing.runWithSpan("receive-parent", consumer::receiveAsync);
    consumer.close();

    assertThatThrownBy(() -> receive.get(1, MINUTES))
        .hasCauseInstanceOf(PulsarClientException.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  @Test
  void failedBatchReceivePreservesParent() throws Exception {
    String topic = "persistent://public/default/failedBatchReceivePreservesParent";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client.newConsumer(Schema.STRING).subscriptionName("test_sub").topic(topic).subscribe();

    consumer.close();
    CompletableFuture<Messages<String>> receive =
        testing.runWithSpan("receive-parent", consumer::batchReceiveAsync);

    assertThatThrownBy(() -> receive.get(1, MINUTES))
        .hasCauseInstanceOf(PulsarClientException.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  static List<AttributeAssertion> sendAttributes(
      String destination, String messageId, boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "pulsar"),
                equalTo(SERVER_ADDRESS, brokerHost),
                equalTo(SERVER_PORT, brokerPort),
                equalTo(MESSAGING_DESTINATION_NAME, destinationName(destination)),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                // messaging.message.body.size requires opt-in
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.pulsar.message.type"), experimental("normal"))));
    if (testHeaders) {
      assertions.add(equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    assertions.add(
        equalTo(MESSAGING_DESTINATION_PARTITION_ID, destinationPartitionId(destination)));
    return assertions;
  }

  static List<AttributeAssertion> batchReceiveAttributes(
      String destination, String messageId, boolean testHeaders) {
    return receiveAttributes(destination, messageId, testHeaders, true);
  }

  private static LinkData batchLink(SpanData producerSpan, String messageId) {
    return LinkData.create(
        asRemote(producerSpan.getSpanContext()), Attributes.of(MESSAGING_MESSAGE_ID, messageId));
  }

  static List<AttributeAssertion> receiveAttributes(
      String destination, String messageId, boolean testHeaders) {
    return receiveAttributes(destination, messageId, testHeaders, false);
  }

  static List<AttributeAssertion> receiveAttributes(
      String destination, String messageId, boolean testHeaders, boolean isBatch) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "pulsar"),
                equalTo(SERVER_ADDRESS, brokerHost),
                equalTo(SERVER_PORT, brokerPort),
                equalTo(MESSAGING_DESTINATION_NAME, destinationName(destination)),
                equalTo(MESSAGING_OPERATION_NAME, "receive"),
                equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                equalTo(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "test_sub"),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null)));
    if (testHeaders) {
      assertions.add(equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    if (isBatch) {
      assertions.add(satisfies(MESSAGING_BATCH_MESSAGE_COUNT, AbstractLongAssert::isPositive));
    }
    assertions.add(
        equalTo(MESSAGING_DESTINATION_PARTITION_ID, destinationPartitionId(destination)));
    return assertions;
  }

  static List<AttributeAssertion> processAttributes(
      String destination, String messageId, boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "pulsar"),
                equalTo(MESSAGING_DESTINATION_NAME, destinationName(destination)),
                equalTo(MESSAGING_OPERATION_NAME, "process"),
                equalTo(MESSAGING_OPERATION_TYPE, "process"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                equalTo(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "test_sub"),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null)));
    if (testHeaders) {
      assertions.add(equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    assertions.add(
        equalTo(MESSAGING_DESTINATION_PARTITION_ID, destinationPartitionId(destination)));
    return assertions;
  }

  // the destination name is fully qualified and excludes the "-partition-N" suffix;
  // the partition is recorded in messaging.destination.partition.id
  static String destinationName(String topic) {

    int suffixIndex = partitionSuffixIndex(topic);
    String destination = suffixIndex == -1 ? topic : topic.substring(0, suffixIndex);
    return TopicName.get(destination).toString();
  }

  private static String destinationPartitionId(String topic) {
    int suffixIndex = partitionSuffixIndex(topic);
    return suffixIndex == -1
        ? null
        : topic.substring(suffixIndex + TopicName.PARTITIONED_TOPIC_SUFFIX.length());
  }

  private static int partitionSuffixIndex(String topic) {
    int partitionIndex = TopicName.getPartitionIndex(topic);
    if (partitionIndex == -1) {
      return -1;
    }
    int suffixIndex = topic.lastIndexOf(TopicName.PARTITIONED_TOPIC_SUFFIX);
    if (suffixIndex == -1
        || !topic
            .substring(suffixIndex + TopicName.PARTITIONED_TOPIC_SUFFIX.length())
            .equals(String.valueOf(partitionIndex))) {
      return -1;
    }
    return suffixIndex;
  }

  static void acknowledgeMessage(Consumer<String> consumer, Message<String> message) {
    try {
      consumer.acknowledge(message);
    } catch (PulsarClientException e) {
      throw new RuntimeException(e);
    }
  }

  static void acknowledgeMessages(Consumer<String> consumer, Messages<String> messages) {
    try {
      consumer.acknowledge(messages);
    } catch (PulsarClientException e) {
      throw new RuntimeException(e);
    }
  }
}
