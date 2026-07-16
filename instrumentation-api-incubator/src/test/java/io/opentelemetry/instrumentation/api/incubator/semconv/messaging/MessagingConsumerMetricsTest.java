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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class MessagingConsumerMetricsTest {

  private static final double[] DURATION_BUCKETS =
      MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void collectsMetricsAndCountsBatchOnce() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener = MessagingConsumerMetrics.get().create(meterProvider.get("test"));

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
                                              equalTo(MESSAGING_CONSUMER_GROUP_NAME, "group"),
                                              equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                              equalTo(
                                                  MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                  "subscription"),
                                              equalTo(
                                                  ERROR_TYPE,
                                                  IllegalStateException.class.getName())))));
    }
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void failedReceiveWithoutBatchCountCountsNoConsumedMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener = MessagingConsumerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "receive" : null)
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
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.client.consumed.messages")
                      .hasLongSumSatisfying(
                          sum -> sum.hasPointsSatisfying(point -> point.hasValue(0))));
    }
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
