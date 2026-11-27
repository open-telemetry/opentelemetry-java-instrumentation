/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessDurationMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricPointCounts;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

@SuppressWarnings({"deprecation", "unchecked"}) // deprecated semconv and generic interceptor mocks
class SpringKafkaInterceptorTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.8";
  private static final SpanContext FIRST_CREATION =
      SpanContext.createFromRemoteParent(
          "11111111111111111111111111111111",
          "1111111111111111",
          TraceFlags.getSampled(),
          TraceState.getDefault());
  private static final SpanContext SECOND_CREATION =
      SpanContext.createFromRemoteParent(
          "22222222222222222222222222222222",
          "2222222222222222",
          TraceFlags.getSampled(),
          TraceState.getDefault());

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final SpringKafkaTelemetry telemetry =
      SpringKafkaTelemetry.builder(testing.getOpenTelemetry())
          .setMessagingReceiveTelemetryEnabled(true)
          .build();

  @Test
  void batchScopeSuppressesNestedRawProcessing() {
    BatchInterceptor<String, String> interceptor = telemetry.createBatchInterceptor();
    ConsumerRecords<String, String> records =
        records(new ConsumerRecord<>("orders", 0, 1, "outer", "value"));
    ConsumerRecord<String, String> nested = new ConsumerRecord<>("orders", 0, 2, "nested", "value");
    Context parent = Context.current();
    interceptor.intercept(records, null);
    Iterator<ConsumerRecord<String, String>> iterator =
        TracingIterator.wrap(
            singletonList(nested).iterator(),
            new KafkaInstrumenterFactory(
                    testing.getOpenTelemetry(), "io.opentelemetry.kafka-clients-2.6")
                .setMessagingReceiveTelemetryEnabled(true)
                .createConsumerProcessInstrumenter(),
            () -> true,
            KafkaConsumerContextUtil.create(null, null, null, null));
    iterator.next();
    assertThat(iterator.hasNext()).isFalse();
    interceptor.success(records, null);
    assertThat(Context.current()).isSameAs(parent);

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span -> assertBatchSpan(span, 1, "0", null).hasNoParent()));
    assertInstrumentationScope();
  }

  @Test
  void retainedRecordRetryRecordsBothAttempts() {
    RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor();
    ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 1, "key", "value");
    Context parentContext = Context.current();
    IllegalStateException error = new IllegalStateException("first attempt");

    assertThat(interceptor.intercept(record, null)).isSameAs(record);
    interceptor.failure(record, error, null);
    assertThat(Context.current()).isSameAs(parentContext);
    assertThat(interceptor.intercept(record, null)).isSameAs(record);
    interceptor.success(record, null);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, record, error).hasNoParent().hasLinks()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, record, null).hasNoParent().hasLinks()));
    assertInstrumentationScope();
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", null, "0", 1, null);
    assertProcessMetricPointCounts(testing, INSTRUMENTATION_NAME, 2);
  }

  @Test
  void nextRecordInThreadStateDoesNotInheritThePreviousFailure() {
    InstrumentedRecordInterceptor<String, String> interceptor =
        (InstrumentedRecordInterceptor<String, String>)
            telemetry.<String, String>createRecordInterceptor();
    ConsumerRecord<String, String> first = new ConsumerRecord<>("orders", 0, 1, "first", "value");
    ConsumerRecord<String, String> second = new ConsumerRecord<>("orders", 0, 2, "second", "value");
    Context parentContext = Context.current();
    IllegalStateException error = new IllegalStateException("first record");

    interceptor.setupThreadState(null);
    try {
      interceptor.intercept(first, null);
      Span firstSpan = Span.current();
      interceptor.failure(first, error, null);
      assertThat(Span.current()).isSameAs(firstSpan);
      interceptor.afterRecord(first, null);
      assertThat(Context.current()).isSameAs(parentContext);

      interceptor.intercept(second, null);
      Span secondSpan = Span.current();
      interceptor.success(second, null);
      assertThat(Span.current()).isSameAs(secondSpan);
      interceptor.afterRecord(second, null);
      assertThat(Context.current()).isSameAs(parentContext);
    } finally {
      interceptor.clearThreadState(null);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, first, error).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, second, null).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void decoratedRecordCompletionCanReenterTheSameInterceptor(boolean failOuter) {
    ConsumerRecord<String, String> outer = new ConsumerRecord<>("orders", 0, 1, "outer", "value");
    ConsumerRecord<String, String> inner = new ConsumerRecord<>("orders", 0, 2, "inner", "value");
    Context parentContext = Context.current();
    KafkaConsumerContextUtil.set(
        inner, KafkaConsumerContextUtil.create(parentContext, null, null, null));
    RecordInterceptor<String, String> decorated = mock();
    when(decorated.intercept(any(), isNull())).thenAnswer(invocation -> invocation.getArgument(0));
    RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor(decorated);
    IllegalStateException error = new IllegalStateException("failed callback");
    AtomicReference<Span> outerSpan = new AtomicReference<>();

    if (failOuter) {
      doAnswer(
              invocation -> {
                interceptor.intercept(inner, null);
                interceptor.success(inner, null);
                assertThat(Span.current()).isSameAs(outerSpan.get());
                return null;
              })
          .when(decorated)
          .failure(eq(outer), eq(error), isNull());
    } else {
      doAnswer(
              invocation -> {
                interceptor.intercept(inner, null);
                interceptor.failure(inner, error, null);
                assertThat(Span.current()).isSameAs(outerSpan.get());
                return null;
              })
          .when(decorated)
          .success(eq(outer), isNull());
    }

    interceptor.intercept(outer, null);
    outerSpan.set(Span.current());
    if (failOuter) {
      interceptor.failure(outer, error, null);
    } else {
      interceptor.success(outer, null);
    }
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, outer, failOuter ? error : null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, inner, failOuter ? null : error).hasNoParent()));
  }

  @Test
  void replacementRecordCompletesTheOriginalAttempt() {
    ConsumerRecord<String, String> original =
        new ConsumerRecord<>("orders", 0, 1, "original", "value");
    ConsumerRecord<String, String> replacement =
        new ConsumerRecord<>("orders", 0, 1, "replacement", "value");
    RecordInterceptor<String, String> decorated = mock();
    when(decorated.intercept(original, null)).thenReturn(replacement);
    RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor(decorated);
    Context parentContext = Context.current();

    assertThat(interceptor.intercept(original, null)).isSameAs(replacement);
    interceptor.success(replacement, null);
    assertThat(Context.current()).isSameAs(parentContext);
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> assertRecordSpan(span, original, null).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void decoratedRecordInterceptNullOrThrowCleansUpBeforeNextRecord(boolean throwFromIntercept) {
    ConsumerRecord<String, String> first = new ConsumerRecord<>("orders", 0, 1, "first", "value");
    ConsumerRecord<String, String> second = new ConsumerRecord<>("orders", 0, 2, "second", "value");
    IllegalStateException error = new IllegalStateException("decorated intercept");
    RecordInterceptor<String, String> decorated = mock();
    when(decorated.intercept(second, null)).thenReturn(second);
    if (throwFromIntercept) {
      when(decorated.intercept(first, null)).thenThrow(error);
    }
    RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor(decorated);
    Context parentContext = Context.current();

    if (throwFromIntercept) {
      assertThatThrownBy(() -> interceptor.intercept(first, null)).isSameAs(error);
    } else {
      assertThat(interceptor.intercept(first, null)).isNull();
    }
    assertThat(Context.current()).isSameAs(parentContext);
    interceptor.intercept(second, null);
    interceptor.success(second, null);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span ->
                assertRecordSpan(span, first, throwFromIntercept ? error : null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, second, null).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void decoratedBatchCompletionCanReenterTheSameInterceptor(boolean failOuter) {
    ConsumerRecords<String, String> outer =
        records(new ConsumerRecord<>("orders", 0, 1, "outer", "value"));
    ConsumerRecords<String, String> inner =
        records(new ConsumerRecord<>("orders", 0, 2, "inner", "value"));
    Context parentContext = Context.current();
    KafkaConsumerContextUtil.set(
        inner, KafkaConsumerContextUtil.create(parentContext, null, null, null));
    BatchInterceptor<String, String> decorated = mock();
    when(decorated.intercept(any(), isNull())).thenAnswer(invocation -> invocation.getArgument(0));
    BatchInterceptor<String, String> interceptor = telemetry.createBatchInterceptor(decorated);
    IllegalStateException error = new IllegalStateException("failed callback");
    AtomicReference<Span> outerSpan = new AtomicReference<>();

    if (failOuter) {
      doAnswer(
              invocation -> {
                interceptor.intercept(inner, null);
                interceptor.success(inner, null);
                assertThat(Span.current()).isSameAs(outerSpan.get());
                return null;
              })
          .when(decorated)
          .failure(eq(outer), eq(error), isNull());
    } else {
      doAnswer(
              invocation -> {
                interceptor.intercept(inner, null);
                interceptor.failure(inner, error, null);
                assertThat(Span.current()).isSameAs(outerSpan.get());
                return null;
              })
          .when(decorated)
          .success(eq(outer), isNull());
    }

    interceptor.intercept(outer, null);
    outerSpan.set(Span.current());
    if (failOuter) {
      interceptor.failure(outer, error, null);
    } else {
      interceptor.success(outer, null);
    }
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", failOuter ? error : null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", failOuter ? null : error).hasNoParent()));
  }

  @Test
  void clearThreadStateClosesCompletedOuterScopeAfterSkippedReentrantBatch() {
    ConsumerRecords<String, String> outer =
        records(new ConsumerRecord<>("orders", 0, 1, "outer", "value"));
    ConsumerRecords<String, String> inner =
        records(new ConsumerRecord<>("orders", 0, 2, "inner", "value"));
    Context enclosing = Context.current();
    KafkaConsumerContextUtil.set(
        inner, KafkaConsumerContextUtil.create(Context.root(), null, null, null));
    BatchInterceptor<String, String> decorated = mock();
    when(decorated.intercept(any(), isNull())).thenAnswer(invocation -> invocation.getArgument(0));
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor(decorated);
    doAnswer(
            invocation -> {
              interceptor.intercept(inner, null);
              return null;
            })
        .when(decorated)
        .success(eq(outer), isNull());

    interceptor.setupThreadState(null);
    interceptor.intercept(outer, null);
    Span outerSpan = Span.current();
    interceptor.success(outer, null);
    assertThat(Span.current()).isNotSameAs(outerSpan);
    interceptor.clearThreadState(null);

    assertThat(Context.current()).isSameAs(enclosing);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent()));
  }

  @SuppressWarnings("removal")
  @Test
  void distinctBatchesWithSharedRecordsPreserveCardinalityAndCreationLinks() {
    ConsumerRecord<String, String> first = record(0, 1, "first", FIRST_CREATION);
    ConsumerRecord<String, String> second = record(1, 2, "second", SECOND_CREATION);
    Map<TopicPartition, List<ConsumerRecord<String, String>>> partitions = new LinkedHashMap<>();
    partitions.put(new TopicPartition("orders", 0), singletonList(first));
    partitions.put(new TopicPartition("orders", 1), singletonList(second));
    ConsumerRecords<String, String> batch = records(partitions);
    ConsumerRecords<String, String> anotherBatch = records(partitions);
    RecordInterceptor<String, String> recordInterceptor = telemetry.createRecordInterceptor();
    BatchInterceptor<String, String> batchInterceptor = telemetry.createBatchInterceptor();
    IllegalStateException error = new IllegalStateException("first batch attempt");

    testing.runWithSpan(
        "parent",
        () -> {
          Context parentContext = Context.current();
          recordInterceptor.intercept(first, null);
          recordInterceptor.success(first, null);
          assertThat(Context.current()).isSameAs(parentContext);
          batchInterceptor.intercept(batch, null);
          batchInterceptor.failure(batch, error, null);
          assertThat(Context.current()).isSameAs(parentContext);
          batchInterceptor.intercept(anotherBatch, null);
          batchInterceptor.success(anotherBatch, null);
          assertThat(Context.current()).isSameAs(parentContext);
        });

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                assertRecordSpan(span, first, null)
                    .hasParent(trace.getSpan(0))
                    .hasLinks(LinkData.create(FIRST_CREATION)),
            span ->
                assertBatchSpan(span, 2, null, error)
                    .hasParent(trace.getSpan(0))
                    .hasLinks(batchLinks()),
            span ->
                assertBatchSpan(span, 2, null, null)
                    .hasParent(trace.getSpan(0))
                    .hasLinks(batchLinks())));
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", null, null, 1, null);
  }

  @Test
  void retryOfSameBatchDoesNotCreateAnotherProcessSpan() {
    ConsumerRecords<String, String> batch =
        records(new ConsumerRecord<>("orders", 0, 1, "first", "value"));
    ConsumerRecords<String, String> independent =
        records(new ConsumerRecord<>("orders", 0, 2, "second", "value"));
    BatchInterceptor<String, String> interceptor = telemetry.createBatchInterceptor();
    IllegalStateException error = new IllegalStateException("first attempt");
    Context parentContext = Context.current();

    interceptor.intercept(batch, null);
    interceptor.failure(batch, error, null);
    assertThat(Context.current()).isSameAs(parentContext);
    interceptor.intercept(batch, null);
    interceptor.success(batch, null);
    assertThat(Context.current()).isSameAs(parentContext);
    interceptor.intercept(independent, null);
    interceptor.success(independent, null);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", error).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent()));
    assertProcessMetricPointCounts(testing, INSTRUMENTATION_NAME, 2);
  }

  @Test
  void batchThreadStateEndsBatchWithoutCompletionCallback() {
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor();
    ConsumerRecords<String, String> outer =
        records(new ConsumerRecord<>("orders", 0, 1, "outer", "value"));
    ConsumerRecords<String, String> skipped =
        records(new ConsumerRecord<>("orders", 0, 2, "skipped", "value"));
    ConsumerRecords<String, String> completed =
        records(new ConsumerRecord<>("orders", 0, 3, "completed", "value"));
    Context parentContext = Context.current();
    KafkaConsumerContextUtil.set(
        skipped, KafkaConsumerContextUtil.create(parentContext, null, null, null));
    KafkaConsumerContextUtil.set(
        completed, KafkaConsumerContextUtil.create(parentContext, null, null, null));

    interceptor.intercept(outer, null);
    Span outerSpan = Span.current();
    try {
      // e.g. the transaction could not be started after the batch was intercepted
      interceptor.setupThreadState(null);
      try {
        interceptor.intercept(skipped, null);
        assertThat(Span.current()).isNotSameAs(outerSpan);
      } finally {
        interceptor.clearThreadState(null);
      }
      assertThat(Span.current()).isSameAs(outerSpan);

      interceptor.setupThreadState(null);
      try {
        interceptor.intercept(completed, null);
        assertThat(Span.current()).isNotSameAs(outerSpan);
        interceptor.success(completed, null);
        assertThat(Span.current()).isSameAs(outerSpan);
      } finally {
        interceptor.clearThreadState(null);
      }
      assertThat(Span.current()).isSameAs(outerSpan);
    } finally {
      interceptor.success(outer, null);
    }
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()));
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", null, "0", 3, null);
  }

  @Test
  void setupFreeSubBatchIsCleanedUpWithoutEndingEnclosingBatch() {
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor();
    ConsumerRecords<String, String> outer =
        records(new ConsumerRecord<>("orders", 0, 1, "outer", "value"));
    ConsumerRecords<String, String> first =
        records(new ConsumerRecord<>("orders", 0, 2, "first", "value"));
    ConsumerRecords<String, String> skipped =
        records(new ConsumerRecord<>("orders", 1, 3, "skipped", "value"));
    Context parent = Context.current();
    KafkaConsumerContextUtil.set(first, KafkaConsumerContextUtil.create(parent, null, null, null));
    KafkaConsumerContextUtil.set(
        skipped, KafkaConsumerContextUtil.create(parent, null, null, null));

    interceptor.intercept(outer, null);
    Context enclosing = Context.current();
    Context afterClear;
    Context skippedContext;
    try {
      interceptor.setupThreadState(null);
      try {
        interceptor.intercept(first, null);
        interceptor.success(first, null);
      } finally {
        interceptor.clearThreadState(null);
      }
      assertThat(Context.current()).isSameAs(enclosing);

      // Spring clears after each sub-batch but sets up only before the original poll.
      interceptor.intercept(skipped, null);
      skippedContext = Context.current();
      interceptor.clearThreadState(null);
      afterClear = Context.current();
      if (skippedContext != enclosing && afterClear == skippedContext) {
        interceptor.success(skipped, null);
      }
    } finally {
      interceptor.success(outer, null);
    }
    assertThat(skippedContext).isNotSameAs(enclosing);
    assertThat(afterClear).isSameAs(enclosing);
    assertThat(Context.current()).isSameAs(parent);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "1", null).hasNoParent()));
  }

  @Test
  void batchThreadStateWithoutSetupIsNoop() {
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor();
    ConsumerRecords<String, String> records =
        records(new ConsumerRecord<>("orders", 0, 1, "key", "value"));
    Context parentContext = Context.current();

    interceptor.intercept(records, null);
    try {
      Span span = Span.current();
      // the container clears the thread state even if it stopped before polling
      interceptor.clearThreadState(null);
      assertThat(Span.current()).isSameAs(span);
    } finally {
      interceptor.success(records, null);
    }
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void clearThreadStateClosesDecoratedInterceptorScopeInStackOrder(boolean attachInSetup) {
    NestedScopeInterceptor decorated = new NestedScopeInterceptor(attachInSetup, null);
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor(decorated);
    ConsumerRecords<String, String> records =
        records(new ConsumerRecord<>("orders", 0, 1, "key", "value"));
    Context enclosing = Context.current();

    interceptor.setupThreadState(null);
    interceptor.intercept(records, null);
    assertThat(Context.current().get(NestedScopeInterceptor.KEY)).isEqualTo("nested");
    interceptor.clearThreadState(null);

    assertThat(Context.current()).isSameAs(enclosing);
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()));
  }

  @Test
  void clearThreadStateClosesSkippedBatchScopeAfterDecoratedScope() {
    ConsumerRecords<String, String> outer =
        records(new ConsumerRecord<>("orders", 0, 1, "outer", "value"));
    ConsumerRecords<String, String> skipped =
        records(new ConsumerRecord<>("orders", 1, 2, "skipped", "value"));
    Context parent = Context.current();
    KafkaConsumerContextUtil.set(
        skipped, KafkaConsumerContextUtil.create(parent, null, null, null));
    AtomicReference<Scope> childScope = new AtomicReference<>();
    BatchInterceptor<String, String> decorated = mock();
    when(decorated.intercept(outer, null)).thenReturn(outer);
    when(decorated.intercept(skipped, null))
        .thenAnswer(invocation -> {
          childScope.set(
              Context.current().with(NestedScopeInterceptor.KEY, "nested").makeCurrent());
          return null;
        });
    doAnswer(
            invocation -> {
              childScope.get().close();
              return null;
            })
        .when(decorated)
        .clearThreadState(null);
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor(decorated);

    interceptor.intercept(outer, null);
    Context enclosing = Context.current();
    interceptor.setupThreadState(null);
    assertThat(interceptor.intercept(skipped, null)).isNull();
    assertThat(Context.current().get(NestedScopeInterceptor.KEY)).isEqualTo("nested");
    interceptor.clearThreadState(null);
    assertThat(Context.current()).isSameAs(enclosing);
    interceptor.success(outer, null);
    assertThat(Context.current()).isSameAs(parent);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "1", null).hasNoParent()));
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", null, "0", 1, null);
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", null, "1", 1, null);
  }

  @Test
  void clearThreadStateEndsBatchWhenDecoratedInterceptorThrows() {
    IllegalStateException failure = new IllegalStateException("clear failed");
    NestedScopeInterceptor decorated = new NestedScopeInterceptor(false, failure);
    InstrumentedBatchInterceptor<String, String> interceptor =
        (InstrumentedBatchInterceptor<String, String>)
            telemetry.<String, String>createBatchInterceptor(decorated);
    ConsumerRecords<String, String> records =
        records(new ConsumerRecord<>("orders", 0, 1, "key", "value"));
    Context enclosing = Context.current();

    interceptor.setupThreadState(null);
    interceptor.intercept(records, null);
    assertThatThrownBy(() -> interceptor.clearThreadState(null)).isSameAs(failure);

    assertThat(Context.current()).isSameAs(enclosing);
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(s -> assertBatchSpan(s, 1, "0", null).hasNoParent()));
  }

  private static class NestedScopeInterceptor implements BatchInterceptor<String, String> {
    static final ContextKey<String> KEY = ContextKey.named("nested");

    private final boolean attachInSetup;
    private final RuntimeException clearFailure;
    private Scope scope;

    NestedScopeInterceptor(boolean attachInSetup, RuntimeException clearFailure) {
      this.attachInSetup = attachInSetup;
      this.clearFailure = clearFailure;
    }

    @Override
    public void setupThreadState(Consumer<?, ?> consumer) {
      if (attachInSetup) {
        scope = Context.current().with(KEY, "nested").makeCurrent();
      }
    }

    @Override
    public ConsumerRecords<String, String> intercept(
        ConsumerRecords<String, String> records, Consumer<String, String> consumer) {
      if (!attachInSetup) {
        scope = Context.current().with(KEY, "nested").makeCurrent();
      }
      return records;
    }

    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
      scope.close();
      if (clearFailure != null) {
        throw clearFailure;
      }
    }
  }

  @Test
  void distinctRecordInterceptorsDoNotConsumeEachOthersCallbackState() {
    RecordInterceptor<String, String> firstInterceptor = telemetry.createRecordInterceptor();
    RecordInterceptor<String, String> secondInterceptor = telemetry.createRecordInterceptor();
    ConsumerRecord<String, String> first = new ConsumerRecord<>("orders", 0, 1, "first", "value");
    ConsumerRecord<String, String> second = new ConsumerRecord<>("orders", 0, 2, "second", "value");
    Context parentContext = Context.current();

    firstInterceptor.intercept(first, null);
    Span firstSpan = Span.current();
    secondInterceptor.intercept(second, null);
    secondInterceptor.success(second, null);
    assertThat(Span.current()).isSameAs(firstSpan);
    firstInterceptor.success(first, null);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span -> assertRecordSpan(span, first, null).hasNoParent()));
  }

  @Test
  void distinctBatchInterceptorsDoNotConsumeEachOthersCallbackState() {
    BatchInterceptor<String, String> firstInterceptor = telemetry.createBatchInterceptor();
    BatchInterceptor<String, String> secondInterceptor = telemetry.createBatchInterceptor();
    ConsumerRecords<String, String> first =
        records(new ConsumerRecord<>("orders", 0, 1, "first", "value"));
    ConsumerRecords<String, String> second =
        records(new ConsumerRecord<>("orders", 0, 2, "second", "value"));
    Context parentContext = Context.current();

    firstInterceptor.intercept(first, null);
    Span firstSpan = Span.current();
    secondInterceptor.intercept(second, null);
    secondInterceptor.success(second, null);
    assertThat(Span.current()).isSameAs(firstSpan);
    firstInterceptor.success(first, null);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span -> assertBatchSpan(span, 1, "0", null).hasNoParent()));
  }

  private static SpanDataAssert assertRecordSpan(
      SpanDataAssert span, ConsumerRecord<String, String> record, Throwable error) {
    return span.hasName("process orders")
        .hasKind(SpanKind.CONSUMER)
        .hasStatus(error == null ? StatusData.unset() : StatusData.error())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "orders"),
            equalTo(MESSAGING_DESTINATION_PARTITION_ID, Integer.toString(record.partition())),
            equalTo(stringKey("kafka.message.key"), record.key()),
            equalTo(longKey("kafka.offset"), record.offset()),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"),
            equalTo(ERROR_TYPE, error != null ? error.getClass().getName() : null));
  }

  private static SpanDataAssert assertBatchSpan(
      SpanDataAssert span, int count, String partition, Throwable error) {
    return span.hasName("process orders")
        .hasKind(SpanKind.CONSUMER)
        .hasStatus(error == null ? StatusData.unset() : StatusData.error())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "orders"),
            equalTo(MESSAGING_DESTINATION_PARTITION_ID, partition),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, count),
            equalTo(ERROR_TYPE, error != null ? error.getClass().getName() : null));
  }

  private static LinkData[] batchLinks() {
    return new LinkData[] {
      LinkData.create(
          FIRST_CREATION,
          Attributes.of(
              MESSAGING_DESTINATION_PARTITION_ID,
              "0",
              longKey("kafka.offset"),
              1L,
              stringKey("kafka.message.key"),
              "first")),
      LinkData.create(
          SECOND_CREATION,
          Attributes.of(
              MESSAGING_DESTINATION_PARTITION_ID,
              "1",
              longKey("kafka.offset"),
              2L,
              stringKey("kafka.message.key"),
              "second"))
    };
  }

  private static ConsumerRecords<String, String> records(ConsumerRecord<String, String> record) {
    return records(
        singletonMap(
            new TopicPartition(record.topic(), record.partition()), singletonList(record)));
  }

  @SuppressWarnings("removal")
  private static <K, V> ConsumerRecords<K, V> records(
      Map<TopicPartition, List<ConsumerRecord<K, V>>> recordsByPartition) {
    return new ConsumerRecords<>(recordsByPartition);
  }

  private static ConsumerRecord<String, String> record(
      int partition, long offset, String key, SpanContext creationContext) {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("orders", partition, offset, key, "value");
    W3CTraceContextPropagator.getInstance()
        .inject(
            Context.root().with(Span.wrap(creationContext)),
            record,
            (carrier, name, value) -> carrier.headers().add(name, value.getBytes(UTF_8)));
    return record;
  }

  private static void assertInstrumentationScope() {
    String version =
        requireNonNull(EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME));
    assertThat(testing.spans())
        .filteredOn(
            span -> span.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
        .isNotEmpty()
        .allSatisfy(
            span -> assertThat(span.getInstrumentationScopeInfo().getVersion()).isEqualTo(version));
  }
}
