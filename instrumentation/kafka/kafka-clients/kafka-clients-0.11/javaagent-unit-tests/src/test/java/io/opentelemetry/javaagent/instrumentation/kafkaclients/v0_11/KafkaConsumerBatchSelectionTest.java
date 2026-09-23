/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Iterator;
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
  void explicitSuppressionAfterIteratorCreationIsHonored() {
    ConsumerRecords<String, String> records = records(record(0));
    KafkaProcessingOwnershipUtil.recordPoll(records, true);
    Iterator<ConsumerRecord<String, String>> iterator = iterator(records);

    InstrumentationUtil.suppressInstrumentation(
        () -> {
          iterator.next();
          assertThat(iterator.hasNext()).isFalse();
        });
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
