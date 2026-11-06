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
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
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
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import org.apache.pulsar.PulsarVersion;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.BatchMessageContainer;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.CryptoKeyReader;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageCrypto;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.interceptor.ProducerInterceptor;
import org.apache.pulsar.client.impl.BatchMessageContainerBase;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.ProducerBuilderImpl;
import org.apache.pulsar.client.impl.ProducerImpl;
import org.apache.pulsar.client.impl.SendCallback;
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
import org.junit.jupiter.params.provider.ValueSource;
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
      assertThat(preparation.get()).isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThat(acknowledgement.get()).isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThat(terminalValue.get()).isEqualTo("caller");
      assertThat(terminalThread.get()).isSameAs(Thread.currentThread());
      assertThat(Context.current().get(key)).isEqualTo("caller");
      assertThat(Span.current().getSpanContext())
          .isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThatThrownBy(future::join).hasCauseInstanceOf(PulsarClientException.class);
      assertThat(testing.spans()).hasSize(1);
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + topic).hasKind(SpanKind.PRODUCER).hasNoParent()));
  }

  @Test
  void directProducerSendAsyncIsInstrumentedOnSuccessAndFailure() throws Exception {
    String topic = "persistent://public/default/directProducerSendAsync";
    ContextKey<String> key = ContextKey.named("pulsar-direct-send-caller");
    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    CountDownLatch successfulSend = new CountDownLatch(1);
    AtomicReference<Context> successfulCallbackContext = new AtomicReference<>();
    AtomicReference<Throwable> successfulCallbackError = new AtomicReference<>();
    AtomicReference<Context> successfulParent = new AtomicReference<>();
    AtomicReference<Message<?>> successfulMessage = new AtomicReference<>();
    testing.runWithSpan(
        "direct-send-success-parent",
        () -> {
          Context caller =
              Baggage.builder()
                  .put("pulsar-test", "success")
                  .build()
                  .storeInContext(Context.current().with(key, "success"));
          successfulParent.set(caller);
          try (Scope ignored = caller.makeCurrent()) {
            sendDirect(
                producer,
                "success",
                successfulCallbackContext,
                successfulCallbackError,
                successfulSend,
                successfulMessage);
          }
        });
    assertThat(successfulSend.await(1, MINUTES)).isTrue();
    assertThat(successfulCallbackError.get()).isNull();
    assertThat(successfulCallbackContext.get().get(key)).isEqualTo("success");
    assertThat(Baggage.fromContext(successfulCallbackContext.get()).getEntryValue("pulsar-test"))
        .isEqualTo("success");
    assertThat(successfulMessage.get().getProperty("traceparent"))
        .startsWith(
            "00-" + Span.fromContext(successfulParent.get()).getSpanContext().getTraceId() + "-")
        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    assertThat(successfulMessage.get().getProperty("traceparent").substring(36, 52))
        .isEqualTo(Span.fromContext(successfulCallbackContext.get()).getSpanContext().getSpanId());
    assertThat(successfulMessage.get().getProperty("baggage")).isEqualTo("pulsar-test=success");

    producer.close();
    CountDownLatch failedSend = new CountDownLatch(1);
    AtomicReference<Context> failedCallbackContext = new AtomicReference<>();
    AtomicReference<Throwable> failedCallbackError = new AtomicReference<>();
    AtomicReference<Context> failedParent = new AtomicReference<>();
    AtomicReference<Message<?>> failedMessage = new AtomicReference<>();
    testing.runWithSpan(
        "direct-send-failure-parent",
        () -> {
          Context caller =
              Baggage.builder()
                  .put("pulsar-test", "failure")
                  .build()
                  .storeInContext(Context.current().with(key, "failure"));
          failedParent.set(caller);
          try (Scope ignored = caller.makeCurrent()) {
            sendDirect(
                producer,
                "failure",
                failedCallbackContext,
                failedCallbackError,
                failedSend,
                failedMessage);
          }
        });
    assertThat(failedSend.await(1, MINUTES)).isTrue();
    assertThat(failedCallbackError.get()).isInstanceOf(PulsarClientException.class);
    assertThat(failedCallbackContext.get().get(key)).isEqualTo("failure");
    assertThat(Baggage.fromContext(failedCallbackContext.get()).getEntryValue("pulsar-test"))
        .isEqualTo("failure");
    assertThat(failedMessage.get().getProperty("traceparent"))
        .startsWith(
            "00-" + Span.fromContext(failedParent.get()).getSpanContext().getTraceId() + "-")
        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    assertThat(failedMessage.get().getProperty("traceparent").substring(36, 52))
        .isEqualTo(Span.fromContext(failedCallbackContext.get()).getSpanContext().getSpanId());
    assertThat(failedMessage.get().getProperty("baggage")).isEqualTo("pulsar-test=failure");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("direct-send-success-parent"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("direct-send-failure-parent"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttribute(ERROR_TYPE, failedCallbackError.get().getClass().getName())));
    assertThat(Span.fromContext(successfulParent.get()).getSpanContext().isValid()).isTrue();
    assertThat(Span.fromContext(failedParent.get()).getSpanContext().isValid()).isTrue();
  }

  private static void sendDirect(
      Producer<String> target,
      String value,
      AtomicReference<Context> callbackContext,
      AtomicReference<Throwable> callbackError,
      CountDownLatch completed,
      AtomicReference<Message<?>> outgoingMessage) {
    CompletableFuture<MessageId> future = new CompletableFuture<>();
    InvocationHandler handler =
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "getFuture":
              return future;
            case "getNextSendCallback":
            case "getNextMessage":
            case "addCallback":
              return null;
            case "sendComplete":
              Throwable error = (Throwable) args[0];
              callbackContext.set(Context.current());
              callbackError.set(error);
              if (error == null) {
                future.complete(null);
              } else {
                future.completeExceptionally(error);
              }
              completed.countDown();
              return null;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        };
    SendCallback callback;
    try {
      callback =
          SendCallback.class.cast(
              new ByteBuddy()
                  .subclass(Object.class)
                  .name(
                      AbstractPulsarClientTest.class.getName()
                          + "$DirectSendCallback"
                          + System.nanoTime())
                  .implement(SendCallback.class)
                  .method(isAbstract())
                  .intercept(InvocationHandlerAdapter.of(handler))
                  .make()
                  .load(SendCallback.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                  .getLoaded()
                  .getDeclaredConstructor()
                  .newInstance());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    Message<?> message =
        ((TypedMessageBuilderImpl<String>) target.newMessage().value(value)).getMessage();
    outgoingMessage.set(message);
    ((ProducerImpl<String>) target).sendAsync(message, callback);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings({"rawtypes", "unchecked"})
  void replacementMessagePropagatesProducerContext(boolean replace) throws Exception {
    String topic =
        "persistent://public/default/replacementMessagePropagatesProducerContext-" + replace;
    ContextKey<String> key = ContextKey.named("pulsar-replacement-caller");
    AtomicReference<SpanContext> interceptorContext = new AtomicReference<>();
    AtomicReference<String> interceptorValue = new AtomicReference<>();
    AtomicReference<String> interceptorBaggage = new AtomicReference<>();
    AtomicReference<String> producerTraceparent = new AtomicReference<>();
    AtomicReference<Message<?>> replacement = new AtomicReference<>();
    AtomicReference<Context> acknowledgementContext = new AtomicReference<>();
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(true)
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
                    interceptorContext.set(Span.current().getSpanContext());
                    interceptorValue.set(Context.current().get(key));
                    interceptorBaggage.set(Baggage.current().getEntryValue("pulsar-test"));
                    producerTraceparent.set(
                        ((MessageImpl<?>) message)
                            .getMessageBuilder().getPropertiesList().stream()
                                .filter(property -> property.getKey().equals("traceparent"))
                                .map(property -> property.getValue())
                                .findFirst()
                                .orElse(null));
                    Message<?> copy = message;
                    if (replace) {
                      copy =
                          ((TypedMessageBuilderImpl<?>)
                                  producer
                                      .newMessage()
                                      .value(message.getValue())
                                      .properties(message.getProperties())
                                      .property("Test-Message-Header", "replacement"))
                              .getMessage();
                    } else {
                      ((MessageImpl<?>) copy).getMessageBuilder().clearProperties();
                      ((MessageImpl<?>) copy)
                          .getMessageBuilder()
                          .addProperty()
                          .setKey("traceparent")
                          .setValue("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
                      ((MessageImpl<?>) copy)
                          .getMessageBuilder()
                          .addProperty()
                          .setKey("baggage")
                          .setValue("pulsar-test=stale");
                      ((MessageImpl<?>) copy)
                          .getMessageBuilder()
                          .addProperty()
                          .setKey("Test-Message-Header")
                          .setValue("mutated");
                    }
                    replacement.set(copy);
                    return copy;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {
                    acknowledgementContext.set(Context.current());
                  }
                })
            .create();

    CompletableFuture<MessageId> future;
    Context caller =
        Baggage.builder()
            .put("pulsar-test", "caller")
            .build()
            .storeInContext(Context.current().with(key, "caller"));
    try (Scope ignored = caller.makeCurrent()) {
      future =
          producer
              .newMessage()
              .value("message")
              .property("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
              .property("baggage", "pulsar-test=stale")
              .property("Test-Message-Header", "original")
              .sendAsync();
    }
    assertThat(interceptorContext.get()).isEqualTo(Span.fromContext(caller).getSpanContext());
    assertThat(interceptorValue.get()).isEqualTo("caller");
    assertThat(interceptorBaggage.get()).isEqualTo("caller");
    assertThat(producerTraceparent.get())
        .isNotNull()
        .isNotEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    assertThat(
            ((MessageImpl<?>) replacement.get())
                .getMessageBuilder().getPropertiesList().stream()
                    .filter(property -> property.getKey().equals("traceparent"))
                    .map(property -> property.getValue()))
        .containsExactly(producerTraceparent.get());
    assertThat(
            ((MessageImpl<?>) replacement.get())
                .getMessageBuilder().getPropertiesList().stream()
                    .filter(property -> property.getKey().equals("baggage"))
                    .map(property -> property.getValue()))
        .containsExactly("pulsar-test=caller");
    consumer =
        client.newConsumer(Schema.STRING).topic(topic).subscriptionName("propagation").subscribe();
    producer.flush();
    MessageId messageId = future.get(1, MINUTES);
    Message<String> received = consumer.receive(10, SECONDS);
    assertThat(received).isNotNull();
    assertThat(received.getProperty("traceparent")).isEqualTo(producerTraceparent.get());
    assertThat(received.getProperty("baggage")).isEqualTo("pulsar-test=caller");
    assertThat(Span.fromContext(acknowledgementContext.get()).getSpanContext())
        .isEqualTo(Span.fromContext(caller).getSpanContext());
    assertThat(acknowledgementContext.get().get(key)).isEqualTo("caller");
    assertThat(Baggage.fromContext(acknowledgementContext.get()).getEntryValue("pulsar-test"))
        .isEqualTo("caller");
    String finalTopic = topic;
    String finalHeader = replace ? "replacement" : "mutated";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send " + finalTopic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasSpanId(producerTraceparent.get().substring(36, 52))
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(finalTopic, messageId.toString(), true, finalHeader))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(CLIENT).hasName("receive " + topic).hasNoParent()));
  }

  @ParameterizedTest
  @SuppressWarnings("rawtypes")
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
    List<String> producerSpanIds = new ArrayList<>();
    List<String> preparationBaggage = new ArrayList<>();
    List<String> acknowledgementValues = new ArrayList<>();
    List<String> acknowledgementBaggage = new ArrayList<>();
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
                    preparationBaggage.add(Baggage.current().getEntryValue("pulsar-test"));
                    String traceparent = (String) message.getProperties().get("traceparent");
                    producerSpanIds.add(traceparent.substring(36, 52));
                    return message;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {
                    acknowledgements.add(Span.current().getSpanContext());
                    acknowledgementValues.add(Context.current().get(key));
                    acknowledgementBaggage.add(Baggage.current().getEntryValue("pulsar-test"));
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
                  producer.newMessage().value("message-" + i).sendAsync();
              String producerSpanId = producerSpanIds.get(i);
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
                                testing.spans().stream()
                                    .filter(span -> span.getKind() == SpanKind.PRODUCER)
                                    .map(span -> span.getSpanContext().getSpanId()))
                            .contains(producerSpanId);
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
      assertThat(preparationBaggage).containsOnly("caller");
      SpanContext callerSpanContext = Span.fromContext(caller.get()).getSpanContext();
      assertThat(preparations).containsOnly(callerSpanContext);
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
    assertThat(acknowledgements).containsOnly(Span.fromContext(caller.get()).getSpanContext());
    assertThat(acknowledgementValues).containsOnly("caller");
    assertThat(acknowledgementBaggage).containsOnly("caller");
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
  @SuppressWarnings({"rawtypes", "unchecked"})
  void linkedBatchAcknowledgementsUseEachCallersContext() throws Exception {
    String topic = "persistent://public/default/linkedBatchAcknowledgementsUseEachCallersContext";
    ContextKey<String> key = ContextKey.named("pulsar-batch-caller");
    Map<String, Context> callers = new ConcurrentHashMap<>();
    Map<String, Context> acknowledgements = new ConcurrentHashMap<>();
    Map<String, Context> continuationsObserved = new ConcurrentHashMap<>();
    CountDownLatch acknowledged = new CountDownLatch(2);
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(true)
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
                    return message;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {
                    String value = (String) message.getValue();
                    acknowledgements.put(value, Context.current());
                    acknowledged.countDown();
                  }
                })
            .create();

    List<CompletableFuture<Void>> continuations = new ArrayList<>();
    for (String value : asList("first", "second")) {
      CompletableFuture<MessageId> future =
          testing.runWithSpan(
              "caller-" + value,
              () -> {
                Context caller =
                    Baggage.builder()
                        .put("pulsar-test", value)
                        .build()
                        .storeInContext(Context.current().with(key, value));
                callers.put(value, caller);
                try (Scope ignored = caller.makeCurrent()) {
                  return producer.newMessage().value(value).sendAsync();
                }
              });
      continuations.add(
          future.thenAccept(unused -> continuationsObserved.put(value, Context.current())));
    }

    producer.flushAsync().get(1, MINUTES);
    assertThat(acknowledged.await(1, MINUTES)).isTrue();
    CompletableFuture.allOf(continuations.toArray(new CompletableFuture<?>[0])).get(1, MINUTES);

    for (String value : asList("first", "second")) {
      Context caller = callers.get(value);
      Context acknowledgement = acknowledgements.get(value);
      Context continuation = continuationsObserved.get(value);
      assertThat(Span.fromContext(acknowledgement).getSpanContext())
          .isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThat(acknowledgement.get(key)).isEqualTo(value);
      assertThat(Baggage.fromContext(acknowledgement).getEntryValue("pulsar-test"))
          .isEqualTo(value);
      assertThat(Span.fromContext(continuation).getSpanContext())
          .isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThat(continuation.get(key)).isEqualTo(value);
      assertThat(Baggage.fromContext(continuation).getEntryValue("pulsar-test")).isEqualTo(value);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller-first"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller-second"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void cancellingPendingLinkedBatchSendPreservesCallerContextUntilAcknowledgement()
      throws Exception {
    String topic =
        "persistent://public/default/cancellingPendingLinkedBatchSendPreservesCallerContext";
    ContextKey<String> key = ContextKey.named("pulsar-cancelled-batch-caller");
    Map<String, Context> callers = new ConcurrentHashMap<>();
    Map<String, Context> acknowledgements = new ConcurrentHashMap<>();
    Map<String, Context> continuationsObserved = new ConcurrentHashMap<>();
    Map<String, CompletableFuture<MessageId>> sends = new HashMap<>();
    CountDownLatch firstAcknowledged = new CountDownLatch(1);
    CountDownLatch continueAcknowledgements = new CountDownLatch(1);
    CountDownLatch allAcknowledged = new CountDownLatch(3);
    AtomicInteger acknowledgementCount = new AtomicInteger();
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(true)
            .batchingMaxPublishDelay(1, HOURS)
            .sendTimeout(0, SECONDS)
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
                    return message;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {
                    acknowledgements.put((String) message.getValue(), Context.current());
                    if (acknowledgementCount.incrementAndGet() == 1) {
                      firstAcknowledged.countDown();
                      try {
                        assertThat(continueAcknowledgements.await(1, MINUTES)).isTrue();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                      }
                    }
                    allAcknowledged.countDown();
                  }
                })
            .create();

    List<CompletableFuture<Void>> continuations = new ArrayList<>();
    for (String value : asList("first", "cancelled", "last")) {
      CompletableFuture<MessageId> future =
          testing.runWithSpan(
              "caller-" + value,
              () -> {
                Context caller =
                    Baggage.builder()
                        .put("pulsar-test", value)
                        .build()
                        .storeInContext(Context.current().with(key, value));
                callers.put(value, caller);
                try (Scope ignored = caller.makeCurrent()) {
                  return producer.newMessage().value(value).sendAsync();
                }
              });
      sends.put(value, future);
      if (!value.equals("cancelled")) {
        continuations.add(
            future.thenAccept(unused -> continuationsObserved.put(value, Context.current())));
      }
    }

    CompletableFuture<Void> flush = producer.flushAsync();
    try {
      assertThat(firstAcknowledged.await(1, MINUTES)).isTrue();
      assertThat(sends.get("cancelled").cancel(false)).isTrue();
      assertThat(sends.get("cancelled")).isCancelled();
    } finally {
      continueAcknowledgements.countDown();
    }
    flush.get(1, MINUTES);
    assertThat(allAcknowledged.await(1, MINUTES)).isTrue();
    CompletableFuture.allOf(continuations.toArray(new CompletableFuture<?>[0])).get(1, MINUTES);
    assertThat(sends.get("first").get(1, MINUTES)).isNotNull();
    assertThat(sends.get("last").get(1, MINUTES)).isNotNull();

    for (String value : asList("first", "cancelled", "last")) {
      Context caller = callers.get(value);
      Context acknowledgement = acknowledgements.get(value);
      assertThat(Span.fromContext(acknowledgement).getSpanContext())
          .isEqualTo(Span.fromContext(caller).getSpanContext());
      assertThat(acknowledgement.get(key)).isEqualTo(value);
      assertThat(Baggage.fromContext(acknowledgement).getEntryValue("pulsar-test"))
          .isEqualTo(value);
      if (!value.equals("cancelled")) {
        Context continuation = continuationsObserved.get(value);
        assertThat(Span.fromContext(continuation).getSpanContext())
            .isEqualTo(Span.fromContext(caller).getSpanContext());
        assertThat(continuation.get(key)).isEqualTo(value);
        assertThat(Baggage.fromContext(continuation).getEntryValue("pulsar-test")).isEqualTo(value);
      }
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller-first"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller-cancelled"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller-last"),
                span ->
                    span.hasName("send " + topic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  @SuppressWarnings("unchecked")
  void messageCryptoHooksUseCallerContext() throws Exception {
    String topic = "persistent://public/default/messageCryptoHooksUseCallerContext";
    String nestedTopic = topic + "-nested";
    ContextKey<String> key = ContextKey.named("pulsar-crypto-caller");
    producer2 = client.newProducer(Schema.STRING).topic(nestedTopic).enableBatching(false).create();
    CallerContextMessageCrypto crypto = new CallerContextMessageCrypto(producer2, true, false);
    ProducerBuilderImpl<String> producerBuilder =
        (ProducerBuilderImpl<String>)
            client
                .newProducer(Schema.STRING)
                .topic(topic)
                .enableBatching(false)
                .defaultCryptoKeyReader("unused")
                .addEncryptionKey("test-key");
    producerBuilder.getConf().setMessageCrypto(crypto);
    producer = producerBuilder.create();

    AtomicReference<Context> caller = new AtomicReference<>();
    AtomicReference<Context> sendReturnContext = new AtomicReference<>();
    CompletableFuture<MessageId> future =
        testing.runWithSpan(
            "crypto-caller",
            () -> {
              Context context =
                  Baggage.builder()
                      .put("pulsar-test", "caller")
                      .build()
                      .storeInContext(Context.current().with(key, "caller"));
              caller.set(context);
              try (Scope ignored = context.makeCurrent()) {
                CompletableFuture<MessageId> sendFuture =
                    producer.newMessage().value("outer").sendAsync();
                sendReturnContext.set(Context.current());
                return sendFuture;
              }
            });
    future.get(1, MINUTES);
    crypto.nestedFuture.get().get(1, MINUTES);

    SpanContext callerSpan = Span.fromContext(caller.get()).getSpanContext();
    for (Context hookContext :
        asList(crypto.maxOutputSizeContext.get(), crypto.encryptContext.get())) {
      assertThat(Span.fromContext(hookContext).getSpanContext()).isEqualTo(callerSpan);
      assertThat(hookContext.get(key)).isEqualTo("caller");
      assertThat(Baggage.fromContext(hookContext).getEntryValue("pulsar-test")).isEqualTo("caller");
    }
    assertThat(Span.fromContext(sendReturnContext.get()).getSpanContext()).isEqualTo(callerSpan);
    assertThat(sendReturnContext.get().get(key)).isEqualTo("caller");
    assertThat(Baggage.fromContext(sendReturnContext.get()).getEntryValue("pulsar-test"))
        .isEqualTo("caller");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("crypto-caller"),
                span -> span.hasName("send " + topic).hasKind(SpanKind.PRODUCER),
                span -> span.hasName("send " + nestedTopic).hasKind(SpanKind.PRODUCER)));
    assertThat(
            testing.spans().stream()
                .filter(span -> span.getKind() == SpanKind.PRODUCER)
                .map(SpanData::getParentSpanId))
        .containsOnly(callerSpan.getSpanId());
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedMessageCryptoHookRestoresCallerContext() throws Exception {
    String topic = "persistent://public/default/failedMessageCryptoHookRestoresCallerContext";
    ContextKey<String> key = ContextKey.named("pulsar-crypto-failure-caller");
    producer2 =
        client.newProducer(Schema.STRING).topic(topic + "-nested").enableBatching(false).create();
    CallerContextMessageCrypto crypto = new CallerContextMessageCrypto(producer2, false, true);
    ProducerBuilderImpl<String> producerBuilder =
        (ProducerBuilderImpl<String>)
            client
                .newProducer(Schema.STRING)
                .topic(topic)
                .enableBatching(false)
                .defaultCryptoKeyReader("unused")
                .addEncryptionKey("test-key");
    producerBuilder.getConf().setMessageCrypto(crypto);
    producer = producerBuilder.create();

    AtomicReference<Context> caller = new AtomicReference<>();
    AtomicReference<Context> sendReturnContext = new AtomicReference<>();
    CompletableFuture<MessageId> future =
        testing.runWithSpan(
            "crypto-failure-caller",
            () -> {
              Context context =
                  Baggage.builder()
                      .put("pulsar-test", "caller")
                      .build()
                      .storeInContext(Context.current().with(key, "caller"));
              caller.set(context);
              try (Scope ignored = context.makeCurrent()) {
                CompletableFuture<MessageId> sendFuture =
                    producer.newMessage().value("message").sendAsync();
                sendReturnContext.set(Context.current());
                return sendFuture;
              }
            });

    assertThatThrownBy(future::join)
        .hasCauseInstanceOf(PulsarClientException.CryptoException.class);
    SpanContext callerSpan = Span.fromContext(caller.get()).getSpanContext();
    assertThat(Span.fromContext(crypto.maxOutputSizeContext.get()).getSpanContext())
        .isEqualTo(callerSpan);
    assertThat(Span.fromContext(crypto.encryptContext.get()).getSpanContext())
        .isEqualTo(callerSpan);
    assertThat(crypto.encryptContext.get().get(key)).isEqualTo("caller");
    assertThat(Baggage.fromContext(crypto.encryptContext.get()).getEntryValue("pulsar-test"))
        .isEqualTo("caller");
    assertThat(Span.fromContext(sendReturnContext.get()).getSpanContext()).isEqualTo(callerSpan);
    assertThat(sendReturnContext.get().get(key)).isEqualTo("caller");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("crypto-failure-caller"),
                span -> span.hasName("send " + topic).hasKind(SpanKind.PRODUCER)));
  }

  @Test
  @SuppressWarnings("rawtypes")
  void nestedSendFromInterceptorUsesCallerContext() throws Exception {
    String topic = "persistent://public/default/nestedSendFromInterceptor";
    String nestedTopic = "persistent://public/default/nestedSendFromInterceptor-nested";
    ContextKey<String> key = ContextKey.named("pulsar-interceptor-caller");
    AtomicReference<SpanContext> interceptorContext = new AtomicReference<>();
    AtomicReference<SpanContext> nestedInterceptorContext = new AtomicReference<>();
    AtomicReference<String> interceptorValue = new AtomicReference<>();
    AtomicReference<String> nestedInterceptorValue = new AtomicReference<>();
    AtomicReference<String> interceptorBaggage = new AtomicReference<>();
    AtomicReference<String> nestedInterceptorBaggage = new AtomicReference<>();
    AtomicReference<CompletableFuture<MessageId>> nestedFuture = new AtomicReference<>();

    producer2 =
        client
            .newProducer(Schema.STRING)
            .topic(nestedTopic)
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
                    nestedInterceptorContext.set(Span.current().getSpanContext());
                    nestedInterceptorValue.set(Context.current().get(key));
                    nestedInterceptorBaggage.set(Baggage.current().getEntryValue("pulsar-test"));
                    return message;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {}
                })
            .create();
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
                    interceptorContext.set(Span.current().getSpanContext());
                    interceptorValue.set(Context.current().get(key));
                    interceptorBaggage.set(Baggage.current().getEntryValue("pulsar-test"));
                    nestedFuture.set(producer2.newMessage().value("nested").sendAsync());
                    return message;
                  }

                  @Override
                  public void onSendAcknowledgement(
                      Producer producer, Message message, MessageId id, Throwable error) {}
                })
            .create();

    Context caller =
        Baggage.builder()
            .put("pulsar-test", "caller")
            .build()
            .storeInContext(Context.current().with(key, "caller"));
    AtomicReference<Context> operationCaller = new AtomicReference<>();
    CompletableFuture<MessageId> outerFuture;
    try (Scope ignored = caller.makeCurrent()) {
      outerFuture =
          testing.runWithSpan(
              "parent",
              () -> {
                operationCaller.set(Context.current());
                return producer.newMessage().value("outer").sendAsync();
              });
    }

    outerFuture.get(1, MINUTES);
    nestedFuture.get().get(1, MINUTES);
    SpanContext callerSpanContext = Span.fromContext(operationCaller.get()).getSpanContext();
    assertThat(interceptorContext.get()).isEqualTo(callerSpanContext);
    assertThat(nestedInterceptorContext.get()).isEqualTo(callerSpanContext);
    assertThat(interceptorValue.get()).isEqualTo("caller");
    assertThat(nestedInterceptorValue.get()).isEqualTo("caller");
    assertThat(interceptorBaggage.get()).isEqualTo("caller");
    assertThat(nestedInterceptorBaggage.get()).isEqualTo("caller");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("send " + topic).hasParent(trace.getSpan(0)),
                span -> span.hasName("send " + nestedTopic).hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customBatcherUsesCallerContext(boolean directSend) throws Exception {
    String topic = "persistent://public/default/customBatcherUsesCallerContext";
    String nestedTopic = topic + "-nested";
    ContextKey<String> key = ContextKey.named("pulsar-batcher-caller");
    List<Context> hookContexts = new ArrayList<>();
    AtomicReference<CompletableFuture<MessageId>> nestedFuture = new AtomicReference<>();
    AtomicReference<Context> nestedCaller = new AtomicReference<>();
    AtomicReference<Context> callbackContext = new AtomicReference<>();
    AtomicReference<Throwable> callbackError = new AtomicReference<>();
    AtomicReference<Message<?>> outgoingMessage = new AtomicReference<>();
    CountDownLatch directSendCompleted = new CountDownLatch(1);
    producer2 = client.newProducer(Schema.STRING).topic(nestedTopic).enableBatching(false).create();
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .batchingMaxPublishDelay(1, HOURS)
            .batcherBuilder(
                () -> {
                  BatchMessageContainer delegate = BatcherBuilder.DEFAULT.build();
                  return (BatchMessageContainer)
                      Proxy.newProxyInstance(
                          BatchMessageContainerBase.class.getClassLoader(),
                          new Class<?>[] {BatchMessageContainerBase.class},
                          (proxy, method, arguments) -> {
                            if (method.getName().equals("haveEnoughSpace")
                                || method.getName().equals("add")) {
                              hookContexts.add(Context.current());
                            }
                            if (method.getName().equals("add")) {
                              Context hookContext = Context.current();
                              testing.runWithSpan(
                                  "batcher",
                                  () -> {
                                    Context nestedContext =
                                        Baggage.builder()
                                            .put("pulsar-test", "nested")
                                            .build()
                                            .storeInContext(Context.current().with(key, "nested"));
                                    nestedCaller.set(nestedContext);
                                    try (Scope ignored = nestedContext.makeCurrent()) {
                                      if (directSend) {
                                        sendDirect(
                                            producer2,
                                            "nested",
                                            callbackContext,
                                            callbackError,
                                            directSendCompleted,
                                            outgoingMessage);
                                      } else {
                                        nestedFuture.set(producer2.sendAsync("nested"));
                                      }
                                      assertCurrentContext(nestedContext, key);
                                    }
                                  });
                              assertCurrentContext(hookContext, key);
                            }
                            return method.invoke(delegate, arguments);
                          });
                })
            .create();
    AtomicReference<Context> caller = new AtomicReference<>();
    Context context =
        Baggage.builder()
            .put("pulsar-test", "caller")
            .build()
            .storeInContext(Context.current().with(key, "caller"));
    Context originalContext = Context.current();
    CompletableFuture<MessageId> future;
    try (Scope ignored = context.makeCurrent()) {
      future =
          testing.runWithSpan(
              "parent",
              () -> {
                caller.set(Context.current());
                CompletableFuture<MessageId> send = producer.sendAsync("message");
                assertThat(Span.current().getSpanContext())
                    .isEqualTo(Span.fromContext(caller.get()).getSpanContext());
                assertThat(Context.current().get(key)).isEqualTo("caller");
                assertThat(Baggage.current().getEntryValue("pulsar-test")).isEqualTo("caller");
                return send;
              });
      assertCurrentContext(context, key);
    }
    assertCurrentContext(originalContext, key);
    assertThat(hookContexts).hasSize(2);
    for (Context hookContext : hookContexts) {
      assertThat(Span.fromContext(hookContext).getSpanContext())
          .isEqualTo(Span.fromContext(caller.get()).getSpanContext());
      assertThat(hookContext.get(key)).isEqualTo("caller");
      assertThat(Baggage.fromContext(hookContext).getEntryValue("pulsar-test")).isEqualTo("caller");
    }
    producer.flush();
    future.get(1, MINUTES);
    if (directSend) {
      assertThat(directSendCompleted.await(1, MINUTES)).isTrue();
      assertThat(callbackError.get()).isNull();
      assertThat(callbackContext.get().get(key)).isEqualTo("nested");
      assertThat(Baggage.fromContext(callbackContext.get()).getEntryValue("pulsar-test"))
          .isEqualTo("nested");
      assertThat(outgoingMessage.get().getProperty("baggage")).isEqualTo("pulsar-test=nested");
      assertThat(outgoingMessage.get().getProperty("traceparent"))
          .startsWith(
              "00-"
                  + Span.fromContext(nestedCaller.get()).getSpanContext().getTraceId()
                  + "-"
                  + Span.fromContext(callbackContext.get()).getSpanContext().getSpanId()
                  + "-")
          .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    } else {
      nestedFuture.get().get(1, MINUTES);
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("send " + topic).hasParent(trace.getSpan(0)),
                span -> span.hasName("batcher").hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("send " + nestedTopic)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(2))
                        .satisfies(
                            spanData -> {
                              if (directSend) {
                                assertThat(
                                        Span.fromContext(callbackContext.get())
                                            .getSpanContext()
                                            .getSpanId())
                                    .isEqualTo(spanData.getSpanId());
                              }
                            })));
  }

  private static void assertCurrentContext(Context expected, ContextKey<String> key) {
    assertThat(Span.current().getSpanContext())
        .isEqualTo(Span.fromContext(expected).getSpanContext());
    assertThat(Context.current().get(key)).isEqualTo(expected.get(key));
    assertThat(Baggage.current().getEntryValue("pulsar-test"))
        .isEqualTo(Baggage.fromContext(expected).getEntryValue("pulsar-test"));
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

  private static class CallerContextMessageCrypto implements MessageCrypto<Object, Object> {

    private final Producer<String> nestedProducer;
    private final boolean sendNested;
    private final boolean fail;
    private final AtomicReference<Context> maxOutputSizeContext = new AtomicReference<>();
    private final AtomicReference<Context> encryptContext = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<MessageId>> nestedFuture =
        new AtomicReference<>();

    private CallerContextMessageCrypto(
        Producer<String> nestedProducer, boolean sendNested, boolean fail) {
      this.nestedProducer = nestedProducer;
      this.sendNested = sendNested;
      this.fail = fail;
    }

    @Override
    public void addPublicKeyCipher(Set<String> keyNames, CryptoKeyReader keyReader) {}

    @Override
    public boolean removeKeyCipher(String keyName) {
      return true;
    }

    @Override
    public int getMaxOutputSize(int inputLen) {
      maxOutputSizeContext.set(Context.current());
      if (sendNested) {
        nestedFuture.set(nestedProducer.sendAsync("nested"));
      }
      return inputLen;
    }

    @Override
    public void encrypt(
        Set<String> encryptionKeys,
        CryptoKeyReader keyReader,
        Supplier<Object> messageMetadataSupplier,
        ByteBuffer payload,
        ByteBuffer encryptedPayload)
        throws PulsarClientException {
      encryptContext.set(Context.current());
      if (fail) {
        throw new PulsarClientException.CryptoException("Test encryption failure");
      }
      encryptedPayload.put(payload);
      encryptedPayload.flip();
    }

    @Override
    public boolean decrypt(
        Supplier<Object> messageMetadataSupplier,
        ByteBuffer payload,
        ByteBuffer decryptedPayload,
        CryptoKeyReader keyReader) {
      return false;
    }
  }

  static List<AttributeAssertion> sendAttributes(
      String destination, String messageId, boolean testHeaders) {
    return sendAttributes(destination, messageId, testHeaders, "test");
  }

  static List<AttributeAssertion> sendAttributes(
      String destination, String messageId, boolean testHeaders, String headerValue) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(stringKey("messaging.system.name"), "pulsar"),
                equalTo(SERVER_ADDRESS, brokerHost),
                equalTo(SERVER_PORT, brokerPort),
                equalTo(MESSAGING_DESTINATION_NAME, destinationName(destination)),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                // messaging.message.body.size requires opt-in
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("pulsar.message.type"), experimental("normal"))));
    if (testHeaders) {
      assertions.add(
          equalTo(headerAttributeKey("Test-Message-Header"), singletonList(headerValue)));
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
                equalTo(stringKey("messaging.system.name"), "pulsar"),
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
                equalTo(stringKey("messaging.system.name"), "pulsar"),
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
