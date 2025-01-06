/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MessagingProducerMetricsTest {

  private static final double[] DURATION_BUCKETS =
      MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = MessagingProducerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "pulsar")
            .put(
                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                "persistent://public/default/topic")
            .put(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish")
            .put(ServerAttributes.SERVER_PORT, 6650)
            .put(ServerAttributes.SERVER_ADDRESS, "localhost")
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, "1:1:0:0")
            .put(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, "1")
            .build();

    Context parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "ff01020304050600ff0a0b0c0d0e0f00",
                        "090a0b0c0d0e0f00",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));

    Context context1 = listener.onStart(parent, requestAttributes, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    Context context2 = listener.onStart(Context.root(), requestAttributes, nanos(150));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.onEnd(context1, responseAttributes, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
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
                                        .hasSum(0.15 /* seconds */)
                                        .hasAttributesSatisfying(
                                            equalTo(
                                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                                "pulsar"),
                                            equalTo(
                                                MessagingIncubatingAttributes
                                                    .MESSAGING_DESTINATION_PARTITION_ID,
                                                "1"),
                                            equalTo(
                                                MessagingIncubatingAttributes
                                                    .MESSAGING_DESTINATION_NAME,
                                                "persistent://public/default/topic"),
                                            equalTo(ServerAttributes.SERVER_PORT, 6650),
                                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00"))
                                        .hasBucketBoundaries(DURATION_BUCKETS))));

    listener.onEnd(context2, responseAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("messaging.publish.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point -> point.hasSum(0.3 /* seconds */))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
