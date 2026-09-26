/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessDurationMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricPointCounts;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
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
  void batchScopeParentsUnrelatedRawProcessing() {
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
            KafkaConsumerContextUtil.create(null, null, null));
    iterator.next();
    assertThat(iterator.hasNext()).isFalse();
    interceptor.success(records, null);
    assertThat(Context.current()).isSameAs(parent);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent(),
                span -> assertRecordSpan(span, nested, null).hasParent(trace.getSpan(0))));
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
    KafkaConsumerContextUtil.set(inner, KafkaConsumerContextUtil.create(parentContext, null, null));
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
    testing.waitAndAssertTraces(
        trace ->
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
            trace.hasSpansSatisfyingExactly(
                span ->
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
    KafkaConsumerContextUtil.set(inner, KafkaConsumerContextUtil.create(parentContext, null, null));
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
  void mixedBatchRetryPreservesCardinalityAndBothCreationLinks() {
    ConsumerRecord<String, String> first = record(0, 1, "first", FIRST_CREATION);
    ConsumerRecord<String, String> second = record(1, 2, "second", SECOND_CREATION);
    Map<TopicPartition, List<ConsumerRecord<String, String>>> partitions = new LinkedHashMap<>();
    partitions.put(new TopicPartition("orders", 0), singletonList(first));
    partitions.put(new TopicPartition("orders", 1), singletonList(second));
    ConsumerRecords<String, String> batch = new ConsumerRecords<>(partitions);
    ConsumerRecords<String, String> retry = new ConsumerRecords<>(partitions);
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
          batchInterceptor.intercept(retry, null);
          batchInterceptor.success(retry, null);
          assertThat(Context.current()).isSameAs(parentContext);
        });

    testing.waitAndAssertTraces(
        trace ->
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertRecordSpan(span, first, null).hasNoParent()));
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertBatchSpan(span, 1, "0", null).hasNoParent()));
  }

  private static SpanDataAssert assertRecordSpan(
      SpanDataAssert span, ConsumerRecord<String, String> record, Throwable error) {
    return span.hasName(emitStableMessagingSemconv() ? "process orders" : "orders process")
        .hasKind(SpanKind.CONSUMER)
        .hasStatus(error == null ? StatusData.unset() : StatusData.error())
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "orders"),
            equalTo(MESSAGING_DESTINATION_PARTITION_ID, Integer.toString(record.partition())),
            equalTo(MESSAGING_KAFKA_MESSAGE_KEY, record.key()),
            equalTo(
                MESSAGING_KAFKA_MESSAGE_OFFSET, emitOldMessagingSemconv() ? record.offset() : null),
            equalTo(MESSAGING_KAFKA_OFFSET, emitStableMessagingSemconv() ? record.offset() : null),
            equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null),
            equalTo(
                ERROR_TYPE,
                emitStableMessagingSemconv() && error != null ? error.getClass().getName() : null));
  }

  private static SpanDataAssert assertBatchSpan(
      SpanDataAssert span, int count, String partition, Throwable error) {
    return span.hasName(emitStableMessagingSemconv() ? "process orders" : "orders process")
        .hasKind(SpanKind.CONSUMER)
        .hasStatus(error == null ? StatusData.unset() : StatusData.error())
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "orders"),
            equalTo(
                MESSAGING_DESTINATION_PARTITION_ID,
                emitStableMessagingSemconv() ? partition : null),
            equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, count),
            equalTo(
                ERROR_TYPE,
                emitStableMessagingSemconv() && error != null ? error.getClass().getName() : null));
  }

  private static LinkData[] batchLinks() {
    return new LinkData[] {
      LinkData.create(
          FIRST_CREATION,
          emitStableMessagingSemconv()
              ? Attributes.of(
                  MESSAGING_DESTINATION_PARTITION_ID, "0",
                  MESSAGING_KAFKA_OFFSET, 1L,
                  MESSAGING_KAFKA_MESSAGE_KEY, "first")
              : Attributes.empty()),
      LinkData.create(
          SECOND_CREATION,
          emitStableMessagingSemconv()
              ? Attributes.of(
                  MESSAGING_DESTINATION_PARTITION_ID, "1",
                  MESSAGING_KAFKA_OFFSET, 2L,
                  MESSAGING_KAFKA_MESSAGE_KEY, "second")
              : Attributes.empty())
    };
  }

  private static ConsumerRecords<String, String> records(ConsumerRecord<String, String> record) {
    return new ConsumerRecords<>(
        singletonMap(
            new TopicPartition(record.topic(), record.partition()), singletonList(record)));
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
