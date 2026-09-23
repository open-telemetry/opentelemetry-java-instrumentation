/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessDurationMetrics;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class ReactorKafkaOwnershipTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.reactor-kafka-1.0";
  private static final TopicPartition PARTITION = new TopicPartition("orders", 0);
  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);
  private static final VirtualField<ConsumerRecord<?, ?>, BooleanSupplier>
      RAW_PROCESSING_ELIGIBILITY = VirtualField.find(ConsumerRecord.class, BooleanSupplier.class);

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void marksOwnershipBeforeAsyncRecordHandoff() {
    SpanContext firstProducer =
        remoteSpanContext("00000000000000000000000000000001", "0000000000000001");
    SpanContext secondProducer =
        remoteSpanContext("00000000000000000000000000000002", "0000000000000002");
    ConsumerRecords<String, String> records = records(record(0, "first"), record(1, "second"));
    prepareContexts(records, firstProducer, secondProducer);
    KafkaConsumerBatchState batchState = prepareRawProcessingEligibility(records);

    Scheduler scheduler = Schedulers.newSingle("reactor-kafka-ownership");
    try {
      List<SpanContext> observedContexts = new ArrayList<>();
      new KafkaClientProcessingHandoffFlux(Flux.just(records))
          .publishOn(scheduler)
          .concatMap(
              batch ->
                  new InstrumentedKafkaFlux<>(
                      Flux.fromIterable(recordsIn((ConsumerRecords<?, ?>) batch))))
          .doOnNext(
              record -> {
                assertThat(batchState.getAsBoolean()).isFalse();
                assertThat(rawProcessingEligibility(record).getAsBoolean()).isFalse();
                assertThat(Span.current().getSpanContext().isValid()).isTrue();
                observedContexts.add(Span.current().getSpanContext());
              })
          .blockLast();

      assertThat(observedContexts).hasSize(2);
      List<SpanData> spans = instrumentationSpans();
      assertThat(spans).hasSize(2);
      assertThat(spans)
          .extracting(SpanData::getParentSpanId)
          .containsExactly(firstProducer.getSpanId(), secondProducer.getSpanId());
      assertThat(spans.get(0).getLinks())
          .extracting(link -> link.getSpanContext().getSpanId())
          .containsExactly(firstProducer.getSpanId());
      assertThat(spans.get(1).getLinks())
          .extracting(link -> link.getSpanContext().getSpanId())
          .containsExactly(secondProducer.getSpanId());
      assertThat(Span.current().getSpanContext().isValid()).isFalse();

      assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", "group", "0", 2, null);
    } finally {
      scheduler.dispose();
    }
  }

  @Test
  void cancellationBeforeOnNextDoesNotMarkOwnership() {
    ConsumerRecords<String, String> records = records(record(0, "value"));
    prepareContexts(
        records, remoteSpanContext("00000000000000000000000000000003", "0000000000000003"));
    KafkaConsumerBatchState batchState = prepareRawProcessingEligibility(records);
    BooleanSupplier recordEligibility = rawProcessingEligibility(recordsIn(records).get(0));

    new KafkaClientProcessingHandoffFlux(Flux.just(records))
        .subscribe(
            new BaseSubscriber<ConsumerRecords<?, ?>>() {
              @Override
              protected void hookOnSubscribe(Subscription subscription) {
                cancel();
              }
            });

    assertThat(batchState.getAsBoolean()).isTrue();
    assertThat(recordEligibility.getAsBoolean()).isTrue();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void failedProcessingCanBeRetried() {
    ConsumerRecord<String, String> record = record(0, "value");
    ConsumerRecords<String, String> records = records(record);
    prepareContexts(
        records, remoteSpanContext("00000000000000000000000000000004", "0000000000000004"));
    IllegalStateException error = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                new InstrumentedKafkaFlux.InstrumentedSubscriber(
                        new CoreSubscriber<ConsumerRecord<?, ?>>() {
                          @Override
                          public void onSubscribe(Subscription subscription) {}

                          @Override
                          public void onNext(ConsumerRecord<?, ?> value) {
                            throw error;
                          }

                          @Override
                          public void onError(Throwable throwable) {}

                          @Override
                          public void onComplete() {}

                          @Override
                          public reactor.util.context.Context currentContext() {
                            return reactor.util.context.Context.empty();
                          }
                        })
                    .onNext(record))
        .isSameAs(error);
    new InstrumentedKafkaFlux<>(Flux.just(record)).blockLast();

    assertThat(instrumentationSpans()).hasSize(2);
    assertProcessDurationMetrics(
        testing, INSTRUMENTATION_NAME, "orders", "group", "0", 1, error.getClass().getName());
    assertProcessDurationMetrics(testing, INSTRUMENTATION_NAME, "orders", "group", "0", 1, null);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  @Test
  void explicitSuppressionDoesNotRecordProcessing() {
    ConsumerRecord<String, String> record = record(0, "value");
    prepareContexts(records(record));

    InstrumentationUtil.suppressInstrumentation(
        () -> new InstrumentedKafkaFlux<>(Flux.just(record)).blockLast());
    assertThat(testing.spans()).isEmpty();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void nestedUnrelatedRecordKeepsItsOwnProcessingSpan() {
    ConsumerRecord<String, String> outerRecord = record(0, "outer");
    ConsumerRecord<String, String> innerRecord = record(1, "inner");
    prepareContexts(records(outerRecord));
    prepareContexts(records(innerRecord));

    try (Scope ignored =
        Baggage.builder()
            .put("tenant", "acme")
            .build()
            .storeInContext(Context.current())
            .makeCurrent()) {
      new InstrumentedKafkaFlux<>(Flux.just(outerRecord))
          .doOnNext(
              outer -> {
                assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("acme");
                new InstrumentedKafkaFlux<>(Flux.just(innerRecord))
                    .doOnNext(
                        inner ->
                            assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("acme"))
                    .blockLast();
              })
          .blockLast();
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
