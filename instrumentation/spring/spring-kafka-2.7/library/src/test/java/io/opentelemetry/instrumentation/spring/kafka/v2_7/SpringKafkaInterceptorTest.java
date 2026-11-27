/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterator;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.listener.BatchConsumerAwareMessageListener;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.Acknowledgment;

@SuppressWarnings({"deprecation", "unchecked"}) // deprecated semconv and generic interceptor mocks
class SpringKafkaInterceptorTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";
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
  void batchListenerPreservesSeekAwareCallbacks() {
    BatchMessageListener<String, String> delegate =
        mock(BatchMessageListener.class, withSettings().extraInterfaces(ConsumerSeekAware.class));
    ConsumerSeekAware seekAware = (ConsumerSeekAware) delegate;
    BatchMessageListener<String, String> listener = telemetry.createBatchMessageListener(delegate);
    assertThat(listener).isInstanceOf(ConsumerSeekAware.class);
    ConsumerSeekAware wrapped = (ConsumerSeekAware) listener;
    ConsumerSeekCallback registered = mock();
    ConsumerSeekCallback assigned = mock();
    ConsumerSeekCallback idle = mock();
    Map<TopicPartition, Long> assignments = singletonMap(new TopicPartition("orders", 0), 42L);
    Map<TopicPartition, Long> idleAssignments = singletonMap(new TopicPartition("orders", 1), 43L);
    List<TopicPartition> revoked = singletonList(new TopicPartition("orders", 2));

    wrapped.registerSeekCallback(registered);
    wrapped.onPartitionsAssigned(assignments, assigned);
    wrapped.onIdleContainer(idleAssignments, idle);
    wrapped.onPartitionsRevoked(revoked);
    wrapped.unregisterSeekCallback();

    verify(seekAware).registerSeekCallback(same(registered));
    verify(seekAware).onPartitionsAssigned(same(assignments), same(assigned));
    verify(seekAware).onIdleContainer(same(idleAssignments), same(idle));
    verify(seekAware).onPartitionsRevoked(same(revoked));
    verify(seekAware).unregisterSeekCallback();
    verifyNoMoreInteractions(seekAware);
  }

  @Test
  void ordinaryBatchListenerDoesNotAdvertiseSeekAwareness() {
    BatchMessageListener<String, String> listener = records -> {};
    assertThat(telemetry.createBatchMessageListener(listener))
        .isNotInstanceOf(ConsumerSeekAware.class);
  }

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

  @Test
  void batchMessageListenerTracesListenerInvocation() {
    ConsumerRecord<String, String> first = new ConsumerRecord<>("orders", 0, 1, "first", "value");
    ConsumerRecord<String, String> second = new ConsumerRecord<>("orders", 0, 2, "second", "value");
    List<ConsumerRecord<String, String>> data = asList(first, second);
    Consumer<String, String> consumer = mock();
    AtomicReference<Span> listenerSpan = new AtomicReference<>();
    AtomicReference<Consumer<?, ?>> listenerConsumer = new AtomicReference<>();
    BatchMessageListener<String, String> listener =
        telemetry.createBatchMessageListener(
            (BatchConsumerAwareMessageListener<String, String>)
                (records, c) -> {
                  assertThat(records).isSameAs(data);
                  listenerSpan.set(Span.current());
                  listenerConsumer.set(c);
                });
    Context parentContext = Context.current();

    listener.onMessage(data, null, consumer);
    assertThat(Context.current()).isSameAs(parentContext);
    assertThat(listenerSpan.get().getSpanContext().isValid()).isTrue();
    assertThat(listenerConsumer.get()).isSameAs(consumer);

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            assertBatchSpan(span, 2, "0", null)
                .hasNoParent()
                .hasSpanId(listenerSpan.get().getSpanContext().getSpanId())));
  }

  @Test
  void batchMessageListenerEndsSpanWhenListenerThrowsAndSkipsRetry() {
    AtomicInteger conversions = new AtomicInteger();
    List<ConsumerRecord<String, String>> data =
        new ArrayList<ConsumerRecord<String, String>>(
            singletonList(new ConsumerRecord<>("orders", 0, 1, "key", "value"))) {
          @Override
          public Object[] toArray() {
            conversions.incrementAndGet();
            return super.toArray();
          }
        };
    List<ConsumerRecord<String, String>> next =
        new ArrayList<ConsumerRecord<String, String>>(
            singletonList(new ConsumerRecord<>("orders", 0, 2, "key", "value"))) {
          @Override
          public Object[] toArray() {
            conversions.incrementAndGet();
            return super.toArray();
          }
        };
    AssertionError error = new AssertionError("listener");
    AtomicReference<Boolean> fail = new AtomicReference<>(true);
    BatchMessageListener<String, String> listener =
        telemetry.createBatchMessageListener(
            (BatchMessageListener<String, String>)
                records -> {
                  if (fail.get()) {
                    testing.runWithSpan(
                        "first listener",
                        () -> {
                          throw error;
                        });
                  } else {
                    testing.runWithSpan(
                        records == data ? "retry listener" : "next listener", () -> {});
                  }
                });
    Context parentContext = Context.current();

    assertThatThrownBy(() -> listener.onMessage(data, null, null)).isSameAs(error);
    assertThat(Context.current()).isSameAs(parentContext);
    fail.set(false);
    // the container retries a failed batch with the same data
    listener.onMessage(data, null, null);
    assertThat(conversions).hasValue(1);
    listener.onMessage(next, null, null);
    assertThat(conversions).hasValue(2);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", error).hasNoParent(),
                span -> span.hasName("first listener").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("retry listener").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent(),
                span -> span.hasName("next listener").hasParent(trace.getSpan(0))));
  }

  @Test
  void distinctBatchMessageListenersDoNotShareRetryState() {
    List<ConsumerRecord<String, String>> data =
        singletonList(new ConsumerRecord<>("orders", 0, 1, "key", "value"));
    BatchMessageListener<String, String> first =
        telemetry.createBatchMessageListener(
            (BatchMessageListener<String, String>)
                records -> testing.runWithSpan("first listener", () -> {}));
    BatchMessageListener<String, String> second =
        telemetry.createBatchMessageListener(
            (BatchMessageListener<String, String>)
                records -> testing.runWithSpan("second listener", () -> {}));
    Context parentContext = Context.current();

    first.onMessage(data);
    assertThat(Context.current()).isSameAs(parentContext);
    second.onMessage(data);
    assertThat(Context.current()).isSameAs(parentContext);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent(),
                span -> span.hasName("first listener").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent(),
                span -> span.hasName("second listener").hasParent(trace.getSpan(0))));
  }

  @Test
  void batchMessageListenerForwardsPollResult() {
    ConsumerRecords<String, String> records =
        records(new ConsumerRecord<>("orders", 0, 1, "key", "value"));
    AtomicReference<Span> listenerSpan = new AtomicReference<>();
    BatchMessageListener<String, String> listener =
        telemetry.createBatchMessageListener(
            new BatchMessageListener<String, String>() {
              @Override
              public void onMessage(List<ConsumerRecord<String, String>> data) {
                throw new UnsupportedOperationException();
              }

              @Override
              public void onMessage(
                  ConsumerRecords<String, String> data,
                  Acknowledgment acknowledgment,
                  Consumer<String, String> consumer) {
                assertThat(data).isSameAs(records);
                listenerSpan.set(Span.current());
              }

              @Override
              public boolean wantsPollResult() {
                return true;
              }
            });

    assertThat(listener.wantsPollResult()).isTrue();
    listener.onMessage(records, null, null);
    assertThat(listenerSpan.get().getSpanContext().isValid()).isTrue();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span -> assertBatchSpan(span, 1, "0", null).hasNoParent()));
  }

  @Test
  void batchMessageListenerProcessesInstrumentedPollResult() {
    TopicPartition partition = new TopicPartition("orders", 0);
    MockConsumer<String, String> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    mockConsumer.assign(singletonList(partition));
    mockConsumer.updateBeginningOffsets(singletonMap(partition, 0L));
    mockConsumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "first"));
    mockConsumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "key", "second"));
    KafkaTelemetry kafkaTelemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(true)
            .build();
    AtomicReference<ConsumerRecords<String, String>> received = new AtomicReference<>();
    BatchMessageListener<String, String> listener =
        telemetry.createBatchMessageListener(
            new BatchMessageListener<String, String>() {
              @Override
              public void onMessage(List<ConsumerRecord<String, String>> data) {
                throw new UnsupportedOperationException();
              }

              @Override
              public void onMessage(
                  ConsumerRecords<String, String> data,
                  Acknowledgment acknowledgment,
                  Consumer<String, String> consumer) {
                received.set(data);
                testing.runWithSpan("listener", () -> {});
              }

              @Override
              public boolean wantsPollResult() {
                return true;
              }
            });

    assertThat(listener.wantsPollResult()).isTrue();
    try (Consumer<String, String> consumer = kafkaTelemetry.instrument(mockConsumer)) {
      testing.runWithSpan(
          "parent",
          () -> {
            Context parent = Context.current();
            ConsumerRecords<String, String> records = consumer.poll(Duration.ZERO);
            assertThat(Context.current()).isSameAs(parent);
            listener.onMessage(records, null, consumer);
            assertThat(received.get()).isSameAs(records);
            assertThat(Context.current()).isSameAs(parent);
          });
    }

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasNoParent(),
            span ->
                span.hasName("poll orders").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
            span ->
                span.hasName("process orders")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfying(
                        equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2),
                        equalTo(MESSAGING_DESTINATION_PARTITION_ID, "0")),
            span -> span.hasName("listener").hasParent(trace.getSpan(2))));
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
}
