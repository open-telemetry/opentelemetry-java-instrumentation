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
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class MessagingProducerMetricsTest {

  private static final double[] DURATION_BUCKETS =
      MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingProducerMetrics.getForOperationType().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_DESTINATION_NAME, "topic")
            .put(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null)
            .put(SERVER_ADDRESS, "localhost")
            .put(SERVER_PORT, 6650)
            .build();
    Attributes responseAttributes =
        Attributes.builder()
            .put(MESSAGING_DESTINATION_PARTITION_ID, "1")
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 2)
            .put(
                ERROR_TYPE,
                emitStableMessagingSemconv() ? IllegalStateException.class.getName() : null)
            .build();

    Context context = listener.onStart(Context.root(), requestAttributes, nanos(100));
    listener.onEnd(context, responseAttributes, nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .hasSize((emitOldMessagingSemconv() ? 1 : 0) + (emitStableMessagingSemconv() ? 2 : 0));

    if (emitOldMessagingSemconv()) {
      assertThat(metrics)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.publish.duration")
                      .hasUnit("s")
                      .hasDescription("Measures the duration of publish operation.")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(0.15)
                                          .hasBucketBoundaries(DURATION_BUCKETS)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                                              equalTo(MESSAGING_OPERATION, "publish"),
                                              equalTo(MESSAGING_DESTINATION_PARTITION_ID, "1"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  emitStableMessagingSemconv()
                                                      ? IllegalStateException.class.getName()
                                                      : null),
                                              equalTo(SERVER_PORT, 6650),
                                              equalTo(SERVER_ADDRESS, "localhost")))));
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
                                          .hasSum(0.15)
                                          .hasBucketBoundaries(DURATION_BUCKETS)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_OPERATION_NAME, "send"),
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(MESSAGING_OPERATION_TYPE, "send"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  IllegalStateException.class.getName()),
                                              equalTo(MESSAGING_DESTINATION_PARTITION_ID, "1")))))
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.client.sent.messages")
                      .hasUnit("{message}")
                      .hasDescription(
                          "Number of messages producer attempted to send to the broker.")
                      .hasLongSumSatisfying(
                          sum ->
                              sum.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasValue(2)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(MESSAGING_OPERATION_NAME, "send"),
                                              equalTo(MESSAGING_SYSTEM, "pulsar"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  IllegalStateException.class.getName()),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(MESSAGING_DESTINATION_PARTITION_ID, "1")))));
    }
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void createDoesNotCountSentMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingProducerMetrics.getForOperationType().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "create" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "create" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "create" : null)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.client.operation.duration"))
                .count())
        .isEqualTo(emitStableMessagingSemconv() ? 1 : 0);
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.client.sent.messages"))
                .count())
        .isZero();
    assertThat(
            metrics.stream()
                .filter(metric -> metric.getName().equals("messaging.publish.duration"))
                .count())
        .isZero();
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void zeroBatchDoesNotCountSentMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener =
        MessagingProducerMetrics.getForOperationType().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null)
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 0)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .noneSatisfy(metric -> assertThat(metric).hasName("messaging.client.sent.messages"));
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void legacyEntryPointAlwaysCollectsLegacyMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener = MessagingProducerMetrics.get().create(meterProvider.get("test"));

    Context context =
        listener.onStart(
            Context.root(),
            Attributes.of(MESSAGING_SYSTEM, "pulsar", MESSAGING_OPERATION, "publish"),
            nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactly(metric -> assertThat(metric).hasName("messaging.publish.duration"));
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
