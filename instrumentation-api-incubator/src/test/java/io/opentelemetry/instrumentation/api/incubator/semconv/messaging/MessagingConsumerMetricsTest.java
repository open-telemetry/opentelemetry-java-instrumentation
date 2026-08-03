/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class MessagingConsumerMetricsTest {

  private static final double[] DURATION_BUCKETS =
      MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  void collectsMetricsAndCountsBatchOnce() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getForOperationTypeWithOldMetrics()
            .create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_DESTINATION_NAME, "topic")
            .put(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_CONSUMER_GROUP_NAME, "group")
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "subscription")
            .build();
    Attributes responseAttributes =
        Attributes.builder()
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 3)
            .put(
                ERROR_TYPE,
                emitStableMessagingSemconv() ? IllegalStateException.class.getName() : null)
            .build();

    Context context = listener.onStart(Context.root(), requestAttributes, nanos(100));
    listener.onEnd(context, responseAttributes, nanos(300));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .hasSize((emitOldMessagingSemconv() ? 2 : 0) + (emitStableMessagingSemconv() ? 2 : 0));

    if (emitOldMessagingSemconv()) {
      assertThat(metrics)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.receive.duration")
                      .hasUnit("s")
                      .hasDescription("Measures the duration of receive operation.")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(0.2)
                                          .hasBucketBoundaries(DURATION_BUCKETS)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(MESSAGING_OPERATION, "receive"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  emitStableMessagingSemconv()
                                                      ? IllegalStateException.class.getName()
                                                      : null)))))
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.receive.messages")
                      .hasUnit("{message}")
                      .hasDescription("Measures the number of received messages.")
                      .hasLongSumSatisfying(
                          sum ->
                              sum.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasValue(3)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(MESSAGING_OPERATION, "receive"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  emitStableMessagingSemconv()
                                                      ? IllegalStateException.class.getName()
                                                      : null)))));
    }
    if (emitStableMessagingSemconv()) {
      assertThat(metrics)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.client.operation.duration")
                      .hasUnit("s")
                      .hasDescription(
                          "Duration of messaging operation initiated by a producer or consumer client.")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(0.2)
                                          .hasBucketBoundaries(DURATION_BUCKETS)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(MESSAGING_CONSUMER_GROUP_NAME, "group"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(
                                                  MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                  "subscription"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  IllegalStateException.class.getName()),
                                              equalTo(MESSAGING_OPERATION_TYPE, "receive")))))
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.client.consumed.messages")
                      .hasUnit("{message}")
                      .hasDescription("Number of messages that were delivered to the application.")
                      .hasLongSumSatisfying(
                          sum ->
                              sum.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasValue(3)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  IllegalStateException.class.getName()),
                                              equalTo(MESSAGING_CONSUMER_GROUP_NAME, "group"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(
                                                  MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                  "subscription")))));
    }
  }

  @Test
  void failedReceiveWithoutBatchCountCountsNoConsumedMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getForOperationTypeWithOldMetrics()
            .create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "receive" : null)
            .build();
    Attributes responseAttributes =
        Attributes.of(ERROR_TYPE, IllegalStateException.class.getName());

    Context context = listener.onStart(Context.root(), requestAttributes, nanos(100));
    listener.onEnd(context, responseAttributes, nanos(300));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    if (emitOldMessagingSemconv()) {
      assertThat(metrics)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.receive.messages")
                      .hasLongSumSatisfying(
                          sum -> sum.hasPointsSatisfying(point -> point.hasValue(1))));
    }
    if (emitStableMessagingSemconv()) {
      assertThat(metrics)
          .anySatisfy(metric -> assertThat(metric).hasName("messaging.client.operation.duration"))
          .noneSatisfy(metric -> assertThat(metric).hasName("messaging.client.consumed.messages"));
    }
  }

  @Test
  void consumedMessagesOnlyCountsFailedDelivery() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getConsumedMessages().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_DESTINATION_NAME, "topic")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(
        context, Attributes.of(ERROR_TYPE, IllegalStateException.class.getName()), nanos(300));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    if (!emitStableMessagingSemconv()) {
      assertThat(metrics).isEmpty();
      return;
    }
    assertThat(metrics)
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasName("messaging.client.consumed.messages")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                                            equalTo(
                                                ERROR_TYPE,
                                                IllegalStateException.class.getName())))));
  }

  @ParameterizedTest
  @MethodSource("nonReceiveOperations")
  void selectsStableMetricsByOperationType(String operationType, boolean recordsClientDuration) {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getForOperationTypeWithOldMetrics()
            .create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operationType : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operationType : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operationType : null)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(300));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.client.operation.duration"))
                .count())
        .isEqualTo(emitStableMessagingSemconv() && recordsClientDuration ? 1 : 0);
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.client.consumed.messages"))
                .count())
        .isZero();
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.receive.duration"))
                .count())
        .isZero();
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.receive.messages"))
                .count())
        .isZero();
  }

  @Test
  void zeroBatchDoesNotCountReceivedMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getForOperationTypeWithOldMetrics()
            .create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 0)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .noneSatisfy(metric -> assertThat(metric).hasName("messaging.receive.messages"));
  }

  private static Stream<Arguments> nonReceiveOperations() {
    return Stream.of(
        argumentSet("process", "process", false), argumentSet("settle", "settle", true));
  }

  @Test
  void endBatchCountWinsOverStartBatchCount() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getForOperationTypeWithOldMetrics()
            .create(meterProvider.get("test"));

    Attributes startAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 3)
            .build();
    Attributes endAttributes = Attributes.of(MESSAGING_BATCH_MESSAGE_COUNT, 5L);

    Context context = listener.onStart(Context.root(), startAttributes, nanos(100));
    listener.onEnd(context, endAttributes, nanos(300));

    // the recorded attributes are start merged with end, so end wins there; the counter increment
    // has to agree with them
    assertThat(metricReader.collectAllMetrics())
        .filteredOn(metric -> metric.getName().endsWith(".messages"))
        .isNotEmpty()
        .allSatisfy(
            metric ->
                assertThat(metric)
                    .hasLongSumSatisfying(
                        sum -> sum.hasPointsSatisfying(point -> point.hasValue(5))));
  }

  @Test
  void operationTypeEntryPointNeverCollectsLegacyMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingConsumerMetrics.getForOperationType().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "receive" : null)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(300));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    if (emitStableMessagingSemconv()) {
      assertThat(metrics)
          .anySatisfy(metric -> assertThat(metric).hasName("messaging.client.operation.duration"))
          .anySatisfy(metric -> assertThat(metric).hasName("messaging.client.consumed.messages"))
          .noneSatisfy(metric -> assertThat(metric).hasName("messaging.receive.duration"))
          .noneSatisfy(metric -> assertThat(metric).hasName("messaging.receive.messages"));
    } else {
      // the stable-only entry point must be completely inert on the default path
      assertThat(metrics).isEmpty();
    }
  }

  @Test
  void legacyEntryPointAlwaysCollectsLegacyMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener = MessagingConsumerMetrics.get().create(meterProvider.get("test"));

    Context context =
        listener.onStart(
            Context.root(),
            Attributes.of(MESSAGING_SYSTEM, "pulsar", MESSAGING_OPERATION, "receive"),
            nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(2)
        .anySatisfy(metric -> assertThat(metric).hasName("messaging.receive.duration"))
        .anySatisfy(metric -> assertThat(metric).hasName("messaging.receive.messages"));
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
