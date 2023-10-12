/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpClientMetricsStableSemconvTest {

  static final double[] DURATION_BUCKETS =
      HttpMetricsUtil.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = HttpClientMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(SemanticAttributes.URL_FULL, "https://localhost:1234/")
            .put(SemanticAttributes.URL_PATH, "/")
            .put(SemanticAttributes.URL_QUERY, "q=a")
            .put(SemanticAttributes.SERVER_ADDRESS, "localhost")
            .put(SemanticAttributes.SERVER_PORT, 1234)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .put(HttpAttributes.ERROR_TYPE, "400")
            .put(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, 100)
            .put(SemanticAttributes.HTTP_RESPONSE_BODY_SIZE, 200)
            .put(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "2.0")
            .put(SemanticAttributes.SERVER_SOCKET_ADDRESS, "1.2.3.4")
            .put(SemanticAttributes.SERVER_SOCKET_DOMAIN, "somehost20")
            .put(SemanticAttributes.SERVER_SOCKET_PORT, 8080)
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
                    .hasName("http.client.request.duration")
                    .hasUnit("s")
                    .hasDescription("Duration of HTTP client requests.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(0.15 /* seconds */)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(
                                                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                            equalTo(HttpAttributes.ERROR_TYPE, "400"),
                                            equalTo(
                                                SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(
                                                SemanticAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(SemanticAttributes.SERVER_ADDRESS, "localhost"),
                                            equalTo(SemanticAttributes.SERVER_PORT, 1234),
                                            equalTo(SemanticAttributes.URL_SCHEME, "https"))
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
                    .hasName("http.client.request.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point -> point.hasSum(0.3 /* seconds */))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
