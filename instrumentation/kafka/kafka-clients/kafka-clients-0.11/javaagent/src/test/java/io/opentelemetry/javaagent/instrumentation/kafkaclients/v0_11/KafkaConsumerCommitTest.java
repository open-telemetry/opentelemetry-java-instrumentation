/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess.getAndResetAdviceFailureCount;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class KafkaConsumerCommitTest {

  private static final String TOPIC = "test.topic";
  private static final TopicPartition TOPIC_PARTITION = new TopicPartition(TOPIC, 0);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void initializeInstrumentation() {
    closedConsumer();
  }

  @Test
  void commitSyncIsDisabledByDefault() {
    assumeFalse(emitStableMessagingSemconv());
    Consumer<Integer, String> consumer = closedConsumer();
    testing.clearData();

    assertThatThrownBy(consumer::commitSync).isInstanceOf(IllegalStateException.class);

    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void commitAsyncIsDisabledByDefault() {
    assumeFalse(emitStableMessagingSemconv());
    Consumer<Integer, String> consumer = closedConsumer();
    testing.clearData();

    assertThatThrownBy(consumer::commitAsync).isInstanceOf(IllegalStateException.class);

    assertThat(testing.spans()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("commitSyncOverloads")
  void commitSyncOverloads(CommitAction commitAction, String expectedDestination) {
    assumeTrue(emitStableMessagingSemconv());
    Consumer<Integer, String> consumer = closedConsumer();
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () ->
            assertThatThrownBy(() -> commitAction.commit(consumer))
                .isInstanceOf(IllegalStateException.class));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCommitSpan(
                            span,
                            trace.getSpan(0),
                            expectedDestination,
                            IllegalStateException.class.getName())
                        .hasStatus(StatusData.error())));
  }

  private static Stream<Arguments> commitSyncOverloads() {
    Stream.Builder<Arguments> arguments = Stream.builder();
    arguments.add(argumentSet("no arguments", (CommitAction) Consumer::commitSync, null));
    arguments.add(
        argumentSet(
            "explicit offsets",
            (CommitAction)
                consumer ->
                    consumer.commitSync(singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1))),
            TOPIC));

    Map<TopicPartition, OffsetAndMetadata> sameTopicBatch = new LinkedHashMap<>();
    sameTopicBatch.put(TOPIC_PARTITION, new OffsetAndMetadata(1));
    sameTopicBatch.put(new TopicPartition(TOPIC, 1), new OffsetAndMetadata(2));
    arguments.add(
        argumentSet(
            "single-topic offset batch",
            (CommitAction) consumer -> consumer.commitSync(sameTopicBatch),
            TOPIC));

    Map<TopicPartition, OffsetAndMetadata> multipleTopicBatch = new LinkedHashMap<>();
    multipleTopicBatch.put(TOPIC_PARTITION, new OffsetAndMetadata(1));
    multipleTopicBatch.put(new TopicPartition("other.topic", 0), new OffsetAndMetadata(2));
    arguments.add(
        argumentSet(
            "multiple-topic offset batch",
            (CommitAction) consumer -> consumer.commitSync(multipleTopicBatch),
            null));

    if (testLatestDeps()) {
      arguments.add(
          argumentSet(
              "timeout",
              (CommitAction) consumer -> invokeCommitSync(consumer, Duration.ofSeconds(5)),
              null));
      arguments.add(
          argumentSet(
              "explicit offsets and timeout",
              (CommitAction)
                  consumer ->
                      invokeCommitSync(
                          consumer,
                          singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1)),
                          Duration.ofSeconds(5)),
              TOPIC));
    }
    return arguments.build();
  }

  @Test
  void commitSyncWithoutAmbientContextCreatesRootSpan() {
    assumeTrue(emitStableMessagingSemconv());
    Consumer<Integer, String> consumer = closedConsumer();
    testing.clearData();

    assertThatThrownBy(consumer::commitSync).isInstanceOf(IllegalStateException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCommitSpan(span, null, null, IllegalStateException.class.getName())
                        .hasStatus(StatusData.error())
                        .hasNoParent()));
  }

  @Test
  void instrumentationFailureDoesNotDisableCommitSpans() {
    assumeTrue(emitStableMessagingSemconv());
    Consumer<Integer, String> consumer = closedConsumer();
    Map<TopicPartition, OffsetAndMetadata> throwingOffsets =
        new AbstractMap<TopicPartition, OffsetAndMetadata>() {
          @Override
          public Set<Entry<TopicPartition, OffsetAndMetadata>> entrySet() {
            throw new IllegalStateException("test instrumentation failure");
          }
        };
    testing.clearData();

    assertThatThrownBy(() -> consumer.commitSync(throwingOffsets))
        .isInstanceOf(IllegalStateException.class);
    assertThat(getAndResetAdviceFailureCount()).isEqualTo(1);
    assertThatThrownBy(consumer::commitSync).isInstanceOf(IllegalStateException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCommitSpan(span, null, null, IllegalStateException.class.getName())
                        .hasStatus(StatusData.error())
                        .hasNoParent()));
  }

  @ParameterizedTest
  @MethodSource("commitAsyncOverloads")
  void commitAsyncPreservesKafkaException(
      AsyncCommitAction commitAction, String expectedDestination) {
    assumeTrue(emitStableMessagingSemconv());
    Consumer<Integer, String> consumer = closedConsumer();
    AtomicInteger callbackCount = new AtomicInteger();
    OffsetCommitCallback callback = (offsets, exception) -> callbackCount.incrementAndGet();
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () ->
            assertThatThrownBy(() -> commitAction.commit(consumer, callback))
                .isInstanceOf(IllegalStateException.class));

    assertThat(callbackCount).hasValue(0);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCommitSpan(
                            span,
                            trace.getSpan(0),
                            expectedDestination,
                            IllegalStateException.class.getName())
                        .hasStatus(StatusData.error())));
  }

  private static Stream<Arguments> commitAsyncOverloads() {
    return Stream.of(
        argumentSet(
            "default offsets and default callback",
            (AsyncCommitAction) (consumer, callback) -> consumer.commitAsync(),
            null),
        argumentSet(
            "default offsets and null callback",
            (AsyncCommitAction) (consumer, callback) -> consumer.commitAsync(null),
            null),
        argumentSet(
            "default offsets and user callback", (AsyncCommitAction) Consumer::commitAsync, null),
        argumentSet(
            "explicit offsets and null callback",
            (AsyncCommitAction)
                (consumer, callback) ->
                    consumer.commitAsync(
                        singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1)), null),
            TOPIC),
        argumentSet(
            "explicit offsets and user callback",
            (AsyncCommitAction)
                (consumer, callback) ->
                    consumer.commitAsync(
                        singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1)), callback),
            TOPIC));
  }

  @Test
  @DisabledIfSystemProperty(named = "otel.instrumentation.common.v3-preview", matches = "true")
  void commitAsyncSpanEndsAtSuccessfulCompletion() {
    assumeTrue(emitStableMessagingSemconv());
    AtomicInteger callbackCount = new AtomicInteger();
    AtomicReference<Exception> callbackException = new AtomicReference<>();
    AtomicReference<OffsetCommitCallback> callback = new AtomicReference<>();
    Map<TopicPartition, OffsetAndMetadata> offsets =
        singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1));
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () ->
            callback.set(
                startAsyncCommit(
                    offsets,
                    (committedOffsets, exception) -> {
                      callbackCount.incrementAndGet();
                      callbackException.set(exception);
                    })));

    assertThat(testing.spans()).hasSize(1);
    callback.get().onComplete(offsets, null);

    assertThat(callbackCount).hasValue(1);
    assertThat(callbackException).hasValue(null);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCommitSpan(span, trace.getSpan(0), TOPIC, null)
                        .hasStatus(StatusData.unset())));
  }

  @Test
  @DisabledIfSystemProperty(named = "otel.instrumentation.common.v3-preview", matches = "true")
  void commitAsyncSpanEndsAtErrorCompletion() {
    assumeTrue(emitStableMessagingSemconv());
    AtomicReference<Exception> callbackException = new AtomicReference<>();
    Map<TopicPartition, OffsetAndMetadata> offsets =
        singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1));
    OffsetCommitCallback callback =
        startAsyncCommit(
            offsets, (committedOffsets, exception) -> callbackException.set(exception));
    CommitFailedException error = new CommitFailedException();
    testing.clearData();

    callback.onComplete(offsets, error);

    assertThat(callbackException).hasValue(error);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCommitSpan(span, null, TOPIC, CommitFailedException.class.getName())
                        .hasStatus(StatusData.error())
                        .hasNoParent()));
  }

  @Test
  @DisabledIfSystemProperty(named = "otel.instrumentation.common.v3-preview", matches = "true")
  void commitAsyncDefaultCallbacksCorrelateReusedOffsets() {
    assumeTrue(emitStableMessagingSemconv());
    Map<TopicPartition, OffsetAndMetadata> offsets =
        singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1));
    List<Exception> callbackExceptions = new ArrayList<>();
    OffsetCommitCallback defaultCallback =
        (committedOffsets, exception) -> callbackExceptions.add(exception);
    AtomicReference<OffsetCommitCallback> firstCallback = new AtomicReference<>();
    AtomicReference<OffsetCommitCallback> secondCallback = new AtomicReference<>();
    CommitFailedException error = new CommitFailedException();
    testing.clearData();

    testing.runWithSpan(
        "first parent",
        () -> firstCallback.set(startAsyncCommitWithDefaultCallback(offsets, defaultCallback)));
    testing.runWithSpan(
        "second parent",
        () -> secondCallback.set(startAsyncCommitWithDefaultCallback(offsets, defaultCallback)));
    assertThat(testing.spans()).hasSize(2);

    secondCallback.get().onComplete(offsets, error);
    firstCallback.get().onComplete(offsets, null);

    assertThat(callbackExceptions).containsExactly(error, null);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCommitSpan(span, trace.getSpan(0), TOPIC, null)
                        .hasStatus(StatusData.unset())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCommitSpan(
                            span, trace.getSpan(0), TOPIC, CommitFailedException.class.getName())
                        .hasStatus(StatusData.error())));
  }

  @Test
  @DisabledIfSystemProperty(named = "otel.instrumentation.common.v3-preview", matches = "true")
  void commitAsyncFutureEndsSpanAtCompletion() {
    assumeTrue(emitStableMessagingSemconv());
    CompletableFuture<Void> future = new CompletableFuture<>();
    testing.clearData();

    KafkaCommitAsyncTracing.AdviceScope adviceScope = KafkaCommitAsyncTracing.start(null, null);
    KafkaCommitAsyncTracing.endOnCompletion(future);
    adviceScope.end(null);
    assertThat(testing.spans()).isEmpty();
    future.complete(null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCommitSpan(span, null, null, null)
                        .hasStatus(StatusData.unset())
                        .hasNoParent()));
  }

  @Test
  @DisabledIfSystemProperty(named = "otel.instrumentation.common.v3-preview", matches = "true")
  void commitAsyncPropagatesCallbackException() {
    assumeTrue(emitStableMessagingSemconv());
    AtomicInteger callbackCount = new AtomicInteger();
    RuntimeException callbackError = new RuntimeException("callback failure");
    Map<TopicPartition, OffsetAndMetadata> offsets =
        singletonMap(TOPIC_PARTITION, new OffsetAndMetadata(1));
    OffsetCommitCallback callback =
        startAsyncCommit(
            offsets,
            (committedOffsets, exception) -> {
              callbackCount.incrementAndGet();
              throw callbackError;
            });
    testing.clearData();

    assertThatThrownBy(() -> callback.onComplete(offsets, null)).isSameAs(callbackError);

    assertThat(callbackCount).hasValue(1);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCommitSpan(span, null, TOPIC, null)
                        .hasStatus(StatusData.unset())
                        .hasNoParent()));
  }

  private static OffsetCommitCallback startAsyncCommit(
      Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
    KafkaCommitAsyncTracing.AdviceScope adviceScope =
        KafkaCommitAsyncTracing.start(offsets, callback);
    OffsetCommitCallback wrappedCallback = adviceScope.callback();
    adviceScope.end(null);
    if (wrappedCallback == null) {
      throw new AssertionError("Callback was not wrapped");
    }
    return wrappedCallback;
  }

  private static OffsetCommitCallback startAsyncCommitWithDefaultCallback(
      Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback defaultCallback) {
    KafkaCommitAsyncTracing.AdviceScope adviceScope = KafkaCommitAsyncTracing.start(offsets, null);
    OffsetCommitCallback wrappedCallback = KafkaCommitAsyncTracing.wrapCallback(defaultCallback);
    adviceScope.end(null);
    return wrappedCallback;
  }

  private static Consumer<Integer, String> closedConsumer() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("bootstrap.servers", "localhost:9092");
    properties.put("group.id", "test");
    properties.put("key.deserializer", IntegerDeserializer.class.getName());
    properties.put("value.deserializer", StringDeserializer.class.getName());
    Consumer<Integer, String> consumer = new KafkaConsumer<>(properties);
    consumer.close();
    return consumer;
  }

  private static SpanDataAssert assertCommitSpan(
      SpanDataAssert span, SpanData parentSpan, String destination, String errorType) {
    span.hasName(destination == null ? "commit" : "commit " + destination)
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_OPERATION_NAME, "commit"),
            equalTo(MESSAGING_OPERATION_TYPE, "settle"),
            equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "settle" : null),
            equalTo(MESSAGING_DESTINATION_NAME, destination),
            equalTo(ERROR_TYPE, errorType));
    if (parentSpan != null) {
      span.hasParent(parentSpan);
    }
    return span;
  }

  private static void invokeCommitSync(Consumer<?, ?> consumer, Object... arguments) {
    Class<?>[] argumentTypes = new Class<?>[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      argumentTypes[i] = arguments[i] instanceof Map ? Map.class : arguments[i].getClass();
    }
    try {
      Method method = consumer.getClass().getMethod("commitSync", argumentTypes);
      method.invoke(consumer, arguments);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new IllegalStateException(cause);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @FunctionalInterface
  private interface CommitAction {
    void commit(Consumer<Integer, String> consumer);
  }

  @FunctionalInterface
  private interface AsyncCommitAction {
    void commit(Consumer<Integer, String> consumer, OffsetCommitCallback callback);
  }
}
