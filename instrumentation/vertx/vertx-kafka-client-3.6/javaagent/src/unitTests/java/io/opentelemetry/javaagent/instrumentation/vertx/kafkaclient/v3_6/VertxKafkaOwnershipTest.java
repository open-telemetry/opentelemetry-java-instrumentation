/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafkaclient.v3_6;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessDurationMetrics;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxKafkaOwnershipTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-kafka-client-3.6";
  private static final TopicPartition PARTITION = new TopicPartition("orders", 0);
  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);
  private static final VirtualField<ConsumerRecord<?, ?>, BooleanSupplier>
      RAW_PROCESSING_ELIGIBILITY = VirtualField.find(ConsumerRecord.class, BooleanSupplier.class);

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void batchAndRecordCallbacksCreateProcessSpans() {
    SpanContext firstProducer =
        remoteSpanContext("00000000000000000000000000000011", "0000000000000011");
    SpanContext secondProducer =
        remoteSpanContext("00000000000000000000000000000012", "0000000000000012");
    ConsumerRecord<String, String> first = record(0, "first");
    ConsumerRecord<String, String> second = record(1, "second");
    ConsumerRecords<String, String> records = records(first, second);
    prepareContexts(records, firstProducer, secondProducer);

    AtomicInteger batchCallbacks = new AtomicInteger();
    AtomicInteger recordCallbacks = new AtomicInteger();
    new InstrumentedBatchRecordsHandler<String, String>(ignored -> batchCallbacks.incrementAndGet())
        .handle(records);
    InstrumentedSingleRecordHandler<String, String> recordHandler =
        new InstrumentedSingleRecordHandler<>(ignored -> recordCallbacks.incrementAndGet());
    recordHandler.handle(first);
    recordHandler.handle(second);

    assertThat(batchCallbacks).hasValue(1);
    assertThat(recordCallbacks).hasValue(2);
    List<SpanData> spans = instrumentationSpans();
    assertThat(spans).hasSize(3);
    SpanData batch =
        spans.stream()
            .filter(
                span ->
                    Long.valueOf(2).equals(span.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT)))
            .findFirst()
            .orElseThrow(AssertionError::new);
    assertThat(batch.getParentSpanContext().isValid()).isFalse();
    assertThat(batch.getLinks())
        .extracting(link -> link.getSpanContext().getSpanId())
        .containsExactly(firstProducer.getSpanId(), secondProducer.getSpanId());
    assertThat(spans)
        .filteredOn(span -> span != batch)
        .extracting(SpanData::getParentSpanId)
        .containsExactly(firstProducer.getSpanId(), secondProducer.getSpanId());
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", "group", "0", 3, null);
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", "group", "0", 3, null);
  }

  @Test
  void failedBatchCanBeRetried() {
    ConsumerRecords<String, String> records = records(record(0, "value"));
    prepareContexts(
        records, remoteSpanContext("00000000000000000000000000000013", "0000000000000013"));

    assertThatThrownBy(
            () ->
                new InstrumentedBatchRecordsHandler<String, String>(
                        ignored -> {
                          throw new IllegalStateException("boom");
                        })
                    .handle(records))
        .isInstanceOf(IllegalStateException.class);
    new InstrumentedBatchRecordsHandler<String, String>(ignored -> {}).handle(records);

    assertThat(instrumentationSpans())
        .hasSize(2)
        .extracting(span -> span.getStatus().getStatusCode())
        .containsExactly(StatusCode.ERROR, StatusCode.UNSET);
    assertProcessDurationMetrics(
        testing,
        INSTRUMENTATION_NAME,
        "orders",
        "group",
        "0",
        1,
        IllegalStateException.class.getName());
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", "group", "0", 1, null);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  @Test
  void nestedUnrelatedRecordKeepsItsOwnProcessingSpan() {
    ConsumerRecords<String, String> outerRecords = records(record(0, "outer"));
    ConsumerRecord<String, String> innerRecord = record(1, "inner");
    prepareContexts(outerRecords);
    prepareContexts(records(innerRecord));

    try (Scope ignored =
        Baggage.builder()
            .put("tenant", "acme")
            .build()
            .storeInContext(Context.current())
            .makeCurrent()) {
      new InstrumentedBatchRecordsHandler<String, String>(
              records -> {
                assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("acme");
                new InstrumentedSingleRecordHandler<String, String>(
                        record ->
                            assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("acme"))
                    .handle(innerRecord);
              })
          .handle(outerRecords);
    }

    List<SpanData> spans = instrumentationSpans();
    assertThat(spans).hasSize(2);
    SpanData inner = spans.get(0);
    SpanData outer = spans.get(1);
    assertThat(inner.getParentSpanId()).isEqualTo(outer.getSpanId());
    assertThat(outer.getParentSpanContext().isValid()).isFalse();
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", "group", "0", 2, null);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(Baggage.current().getEntryValue("tenant")).isNull();
  }

  @Test
  void repeatedHandoffKeepsAdapterOwnership() {
    ConsumerRecords<String, String> records = records(record(0, "value"));
    prepareContexts(records);
    KafkaConsumerBatchState batchState = prepareRawProcessingEligibility(records);
    ConsumerRecord<?, ?> record = recordsIn(records).get(0);
    BooleanSupplier recordEligibility = rawProcessingEligibility(record);

    KafkaReadStreamImplInstrumentation.DispatchAdvice.onEnter(new Object[] {records});
    KafkaReadStreamImplInstrumentation.DispatchAdvice.onEnter(new Object[] {records});

    assertThat(batchState.getAsBoolean()).isFalse();
    assertThat(recordEligibility.getAsBoolean()).isFalse();
  }

  @Test
  void absentAdapterHandoffLeavesRawProcessingEnabled() {
    ConsumerRecords<String, String> records = records(record(0, "value"));
    prepareContexts(records);
    KafkaConsumerBatchState batchState = prepareRawProcessingEligibility(records);
    BooleanSupplier recordEligibility = rawProcessingEligibility(recordsIn(records).get(0));

    assertThat(batchState.getAsBoolean()).isTrue();
    assertThat(recordEligibility.getAsBoolean()).isTrue();
  }

  private static KafkaConsumerBatchState prepareRawProcessingEligibility(
      ConsumerRecords<?, ?> records) {
    KafkaConsumerBatchState batchState = new KafkaConsumerBatchState(true);
    BATCH_STATE.set(records, batchState);
    for (ConsumerRecord<?, ?> record : recordsIn(records)) {
      RAW_PROCESSING_ELIGIBILITY.set(record, new KafkaConsumerBatchState(true));
    }
    return batchState;
  }

  private static BooleanSupplier rawProcessingEligibility(ConsumerRecord<?, ?> record) {
    return RAW_PROCESSING_ELIGIBILITY.get(record);
  }

  private static void prepareContexts(
      ConsumerRecords<?, ?> records, SpanContext... producerContexts) {
    KafkaConsumerContext batchContext = KafkaConsumerContextUtil.create(null, "group", "client");
    KafkaConsumerContextUtil.set(records, batchContext);
    List<ConsumerRecord<?, ?>> recordList = recordsIn(records);
    for (int i = 0; i < recordList.size(); i++) {
      ConsumerRecord<?, ?> record = recordList.get(i);
      Context context =
          i < producerContexts.length ? Context.root().with(Span.wrap(producerContexts[i])) : null;
      if (i < producerContexts.length) {
        SpanContext producerContext = producerContexts[i];
        record
            .headers()
            .add(
                "traceparent",
                ("00-" + producerContext.getTraceId() + "-" + producerContext.getSpanId() + "-01")
                    .getBytes(UTF_8));
      }
      KafkaConsumerContextUtil.set(
          record, KafkaConsumerContextUtil.create(context, "group", "client"));
    }
  }

  private static List<ConsumerRecord<?, ?>> recordsIn(ConsumerRecords<?, ?> records) {
    List<ConsumerRecord<?, ?>> result = new ArrayList<>();
    for (TopicPartition partition : records.partitions()) {
      List<? extends ConsumerRecord<?, ?>> partitionRecords = records.records(partition);
      for (int i = 0; i < partitionRecords.size(); i++) {
        result.add(partitionRecords.get(i));
      }
    }
    return result;
  }

  private static List<SpanData> instrumentationSpans() {
    return testing.spans().stream()
        .filter(span -> span.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
        .collect(toList());
  }

  private static SpanContext remoteSpanContext(String traceId, String spanId) {
    return SpanContext.createFromRemoteParent(
        traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
  }

  private static ConsumerRecord<String, String> record(long offset, String value) {
    return new ConsumerRecord<>("orders", 0, offset, "key", value);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static <K, V> ConsumerRecords<K, V> records(ConsumerRecord<K, V>... records) {
    return new ConsumerRecords<>(singletonMap(PARTITION, asList(records)));
  }
}
