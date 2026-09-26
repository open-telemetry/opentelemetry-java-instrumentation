/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterable;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterator;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingList;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaConsumerBatchSelectionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final KafkaInstrumenterFactory factory =
      new KafkaInstrumenterFactory(
              testing.getOpenTelemetry(), "io.opentelemetry.kafka-clients-0.11")
          .setMessagingReceiveTelemetryEnabled(false);

  @Test
  void iteratorCreatedBeforePollStateObservesLaterSelection() {
    ConsumerRecords<String, String> records = records(record(0));
    Iterator<ConsumerRecord<String, String>> iterator = iterator(records);
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records);

    assertThat(iterator.next().offset()).isZero();
    assertThat(iterator.hasNext()).isFalse();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void partialBatchAndCopiedRecordSelectOnlyTheirDelivery() {
    ConsumerRecord<String, String> selected = record(0);
    ConsumerRecord<String, String> unrelated = record(1);
    ConsumerRecords<String, String> records = records(selected, unrelated);
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Iterator<ConsumerRecord<String, String>> iterator = iterator(records);
    ConsumerRecord<String, String> copy = record(0);
    KafkaConsumerContextUtil.copy(selected, copy);
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records(copy));

    assertThat(iterator.next()).isSameAs(selected);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(iterator.next()).isSameAs(unrelated);
    assertThat(Span.current().getSpanContext().isValid()).isTrue();
    assertThat(iterator.hasNext()).isFalse();
    assertProcessSpans(1);
  }

  @Test
  void disabledFrameworkLeavesRawFallback() {
    ConsumerRecords<String, String> records = records(record(0), record(1));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    Iterator<ConsumerRecord<String, String>> iterator = iterator(records);
    while (iterator.hasNext()) {
      iterator.next();
    }
    assertProcessSpans(2);
  }

  @Test
  void unrelatedRawBatchRemainsVisibleInsideFrameworkBatch() {
    ConsumerRecords<String, String> frameworkRecords = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(frameworkRecords, true);
    Iterator<ConsumerRecord<String, String>> earlyIterator = iterator(frameworkRecords);
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(frameworkRecords);
    Instrumenter<KafkaReceiveRequest, Void> framework = factory.createBatchProcessInstrumenter();
    KafkaReceiveRequest request = KafkaReceiveRequest.create(frameworkRecords, "group", "client");
    Context context = framework.start(Context.current(), request);
    try (Scope ignored = context.makeCurrent()) {
      ConsumerRecords<String, String> nestedRecords = records(record(1));
      KafkaProcessingOwnershipUtil.recordPoll(nestedRecords, true);
      Iterator<ConsumerRecord<String, String>> nested = iterator(nestedRecords);
      nested.next();
      assertThat(nested.hasNext()).isFalse();
      assertThat(Span.current()).isSameAs(Span.fromContext(context));
      earlyIterator.next();
      assertThat(earlyIterator.hasNext()).isFalse();
      assertThat(Span.current()).isSameAs(Span.fromContext(context));
    } finally {
      framework.end(context, request, null, null);
    }

    assertProcessSpans(2);
    assertThat(testing.spans())
        .anySatisfy(
            span ->
                assertThat(span.getParentSpanId())
                    .isEqualTo(Span.fromContext(context).getSpanContext().getSpanId()));
    if (emitStableMessagingSemconv()) {
      assertThat(testing.metrics())
          .filteredOn(metric -> metric.getName().equals("messaging.process.duration"))
          .singleElement()
          .satisfies(
              metric ->
                  assertThat(
                          metric.getHistogramData().getPoints().stream()
                              .mapToLong(point -> point.getCount())
                              .sum())
                      .isEqualTo(2));
    }
  }

  @Test
  void selectionOnAnotherThreadAffectsExistingIterator() throws InterruptedException {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Iterator<ConsumerRecord<String, String>> iterator = iterator(records);
    Thread framework =
        new Thread(
            () -> KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records));
    framework.start();
    framework.join();

    iterator.next();
    assertThat(iterator.hasNext()).isFalse();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void newPollResetsSelectionOnReusedBatch() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records);
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    Iterator<ConsumerRecord<String, String>> iterator = iterator(records);
    iterator.next();
    assertThat(iterator.hasNext()).isFalse();
    assertProcessSpans(1);
  }

  @Test
  void unconsumedIteratorDoesNotBlockOtherViews() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Iterator<ConsumerRecord<String, String>> unused = iterable(records).iterator();
    assertThat(unused.hasNext()).isTrue();
    List<ConsumerRecord<String, String>> partition = list(records);

    partition.forEach(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    ListIterator<ConsumerRecord<String, String>> subList = partition.subList(0, 1).listIterator();
    assertThat(subList.next().offset()).isZero();
    assertThat(subList.hasNext()).isFalse();
    assertProcessSpans(2);
  }

  @Test
  void subListAndRootListTraceEachTraversal() {
    ConsumerRecords<String, String> records = records(record(0), record(1));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    List<ConsumerRecord<String, String>> list = list(records);
    List<ConsumerRecord<String, String>> subList = list.subList(0, 1);
    Iterator<ConsumerRecord<String, String>> first = subList.iterator();
    assertThat(first.next().offset()).isZero();
    assertThat(first.hasNext()).isFalse();

    list.forEach(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    subList.forEach(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    assertProcessSpans(4);
  }

  @Test
  void unusedListIteratorDoesNotBlockNextTraversal() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    List<ConsumerRecord<String, String>> list = list(records);
    assertThat(list.listIterator().hasNext()).isTrue();

    Iterator<ConsumerRecord<String, String>> nextPass = list.iterator();
    assertThat(nextPass.next().offset()).isZero();
    assertThat(nextPass.hasNext()).isFalse();
    assertProcessSpans(1);
  }

  @Test
  void directIteratorAndPartitionListBothTrace() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Iterator<ConsumerRecord<String, String>> first = iterator(records);
    assertThat(first.hasNext()).isTrue();

    ListIterator<ConsumerRecord<String, String>> second = list(records).listIterator();
    assertThat(second.next().offset()).isZero();
    assertThat(second.hasNext()).isFalse();
    assertThat(first.next().offset()).isZero();
    assertThat(first.hasNext()).isFalse();
    assertProcessSpans(2);
  }

  @Test
  void topicAndPartitionForEachBothCloseSpans() {
    ConsumerRecords<String, String> records = records(record(0), record(1));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    iterable(records)
        .forEach(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    list(records).forEach(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());

    assertProcessSpans(4);
  }

  @Test
  void unusedSpliteratorDoesNotBlockNextTraversal() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    List<ConsumerRecord<String, String>> list = list(records);
    Spliterator<ConsumerRecord<String, String>> first = list.subList(0, 1).spliterator();
    assertThat(first.estimateSize()).isEqualTo(1);

    Iterator<ConsumerRecord<String, String>> nextPass = list.iterator();
    assertThat(nextPass.next().offset()).isZero();
    assertThat(nextPass.hasNext()).isFalse();
    assertProcessSpans(1);
  }

  @Test
  void reverseListIteratorEndsForwardSpan() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    ListIterator<ConsumerRecord<String, String>> iterator = list(records).listIterator();
    assertThat(iterator.next().offset()).isZero();
    assertThat(Span.current().getSpanContext().isValid()).isTrue();
    assertThat(iterator.previous().offset()).isZero();

    assertProcessSpans(1);
  }

  @Test
  void forEachClosesSpanAfterEachCallbackAndOnThrow() {
    ConsumerRecords<String, String> records = records(record(0), record(1));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    List<ConsumerRecord<String, String>> list = list(records);

    assertThatThrownBy(
            () ->
                list.forEach(
                    record -> {
                      assertThat(Span.current().getSpanContext().isValid()).isTrue();
                      throw new IllegalStateException("callback failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("callback failed");

    Iterator<ConsumerRecord<String, String>> nextPass = list.iterator();
    assertThat(nextPass.next().offset()).isZero();
    assertThat(nextPass.hasNext()).isTrue();
    assertThat(nextPass.next().offset()).isEqualTo(1);
    assertThat(nextPass.hasNext()).isFalse();
    assertProcessSpans(3);
  }

  @Test
  void invalidCallbackDoesNotBlockLaterTraversal() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    List<ConsumerRecord<String, String>> list = list(records);

    assertThatThrownBy(() -> list.forEach(null)).isInstanceOf(NullPointerException.class);
    Iterator<ConsumerRecord<String, String>> nextPass = list.iterator();
    assertThat(nextPass.next().offset()).isZero();
    assertThat(nextPass.hasNext()).isFalse();
    assertProcessSpans(1);
  }

  @Test
  void splitSpliteratorAndLaterForEachCloseEachCallback() {
    ConsumerRecords<String, String> records = records(record(0), record(1), record(2));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    List<ConsumerRecord<String, String>> list = list(records);
    Spliterator<ConsumerRecord<String, String>> tail = list.spliterator();
    Spliterator<ConsumerRecord<String, String>> head = tail.trySplit();
    assertThat(head).isNotNull();

    head.forEachRemaining(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    tail.forEachRemaining(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    list.forEach(record -> assertThat(Span.current().getSpanContext().isValid()).isTrue());
    assertProcessSpans(6);
  }

  @Test
  void spliteratorCallbackThrowClosesSpan() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Spliterator<ConsumerRecord<String, String>> spliterator = list(records).spliterator();

    assertThatThrownBy(
            () ->
                spliterator.tryAdvance(
                    record -> {
                      assertThat(Span.current().getSpanContext().isValid()).isTrue();
                      throw new IllegalStateException("callback failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("callback failed");

    assertProcessSpans(1);
  }

  @Test
  void splitCallbackThrowDoesNotLeakIntoParentTraversal() {
    ConsumerRecords<String, String> records = records(record(0), record(1));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Spliterator<ConsumerRecord<String, String>> tail = list(records).spliterator();
    Spliterator<ConsumerRecord<String, String>> head = tail.trySplit();
    assertThat(head).isNotNull();

    assertThatThrownBy(
            () ->
                head.tryAdvance(
                    record -> {
                      throw new IllegalStateException("callback failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("callback failed");
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(
            tail.tryAdvance(
                record -> assertThat(Span.current().getSpanContext().isValid()).isTrue()))
        .isTrue();
    assertProcessSpans(2);
  }

  @Test
  void frameworkHandoffAfterSpliteratorAcquisitionSuppressesProcessSpan() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Spliterator<ConsumerRecord<String, String>> spliterator = list(records).spliterator();
    KafkaProcessingOwnershipUtil.markProcessingOwnedOutsideKafkaClient(records);

    assertThat(spliterator.tryAdvance(record -> {})).isTrue();
    list(records).forEach(record -> {});
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void repeatedDirectIteratorsEachTrace() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);

    for (int pass = 0; pass < 2; pass++) {
      Iterator<ConsumerRecord<String, String>> iterator = iterator(records);
      assertThat(iterator.next().offset()).isZero();
      assertThat(iterator.hasNext()).isFalse();
    }
    assertProcessSpans(2);
  }

  private List<ConsumerRecord<String, String>> list(ConsumerRecords<String, String> records) {
    return TracingList.wrap(
        records.records(new TopicPartition("orders", 0)),
        factory.createConsumerProcessInstrumenter(),
        KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true),
        KafkaConsumerContextUtil.create(null, "group", "client"));
  }

  private Iterable<ConsumerRecord<String, String>> iterable(
      ConsumerRecords<String, String> records) {
    return TracingIterable.wrap(
        records.records("orders"),
        factory.createConsumerProcessInstrumenter(),
        KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true),
        KafkaConsumerContextUtil.create(null, "group", "client"));
  }

  private Iterator<ConsumerRecord<String, String>> iterator(
      ConsumerRecords<String, String> records) {
    Instrumenter<KafkaProcessRequest, Void> instrumenter =
        factory.createConsumerProcessInstrumenter();
    return TracingIterator.wrap(
        records.iterator(),
        instrumenter,
        KafkaProcessingOwnershipUtil.rawProcessingEligibility(records, () -> true),
        KafkaConsumerContextUtil.create(null, "group", "client"));
  }

  private static void assertProcessSpans(int count) {
    assertThat(testing.spans())
        .hasSize(count)
        .extracting(SpanData::getKind)
        .containsOnly(SpanKind.CONSUMER);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static ConsumerRecords<String, String> records(
      ConsumerRecord<String, String>... records) {
    return new ConsumerRecords<>(singletonMap(new TopicPartition("orders", 0), asList(records)));
  }

  private static ConsumerRecord<String, String> record(long offset) {
    return new ConsumerRecord<>("orders", 0, offset, "key", "value");
  }
}
