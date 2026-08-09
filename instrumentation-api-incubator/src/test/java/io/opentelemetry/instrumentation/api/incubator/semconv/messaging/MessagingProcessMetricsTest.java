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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
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
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

class MessagingProcessMetricsTest {

  private static final double[] DURATION_BUCKETS =
      MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void emitsProcessDurationAccordingToConfiguration() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    OperationListener listener = MessagingProcessMetrics.get().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_DESTINATION_NAME, "topic")
            .put(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}")
            .put(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null)
            .put(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null)
            .put(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null)
            .build();

    Context root = Context.root();
    Context context = listener.onStart(root, attributes, nanos(100));
    Attributes endAttributes =
        Attributes.builder()
            .put(
                ERROR_TYPE,
                emitStableMessagingSemconv() ? IllegalStateException.class.getName() : null)
            .build();
    listener.onEnd(context, endAttributes, nanos(350));

    if (!emitStableMessagingSemconv()) {
      assertThat(context).isSameAs(root);
      assertThat(metricReader.collectAllMetrics()).isEmpty();
      return;
    }

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasName("messaging.process.duration")
                    .hasUnit("s")
                    .hasDescription("Duration of processing operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(0.25)
                                        .hasBucketBoundaries(DURATION_BUCKETS)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                            equalTo(
                                                ERROR_TYPE,
                                                IllegalStateException.class.getName())))));
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
