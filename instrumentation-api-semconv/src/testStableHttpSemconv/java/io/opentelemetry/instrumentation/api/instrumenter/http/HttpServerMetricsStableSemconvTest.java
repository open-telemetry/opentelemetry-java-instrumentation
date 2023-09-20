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

class HttpServerMetricsStableSemconvTest {

  static final double[] DURATION_BUCKETS =
      HttpMetricsUtil.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = HttpServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(SemanticAttributes.URL_SCHEME, "https")
            .put(SemanticAttributes.URL_PATH, "/")
            .put(SemanticAttributes.URL_QUERY, "q=a")
            .put(SemanticAttributes.NETWORK_TRANSPORT, "tcp")
            .put(SemanticAttributes.NETWORK_TYPE, "ipv4")
            .put(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "2.0")
            .put(SemanticAttributes.SERVER_ADDRESS, "localhost")
            .put(SemanticAttributes.SERVER_PORT, 1234)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .put(HttpAttributes.ERROR_TYPE, "500")
            .put(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, 100)
            .put(SemanticAttributes.HTTP_RESPONSE_BODY_SIZE, 200)
            .put(SemanticAttributes.CLIENT_SOCKET_ADDRESS, "1.2.3.4")
            .put(SemanticAttributes.CLIENT_SOCKET_PORT, 8080)
            .put(SemanticAttributes.SERVER_SOCKET_ADDRESS, "4.3.2.1")
            .put(SemanticAttributes.SERVER_SOCKET_PORT, 9090)
            .build();

    SpanContext spanContext1 =
        SpanContext.create(
            "ff01020304050600ff0a0b0c0d0e0f00",
            "090a0b0c0d0e0f00",
            TraceFlags.getSampled(),
            TraceState.getDefault());
    SpanContext spanContext2 =
        SpanContext.create(
            "123456789abcdef00000000000999999",
            "abcde00000054321",
            TraceFlags.getSampled(),
            TraceState.getDefault());

    Context parent1 = Context.root().with(Span.wrap(spanContext1));
    Context context1 = listener.onStart(parent1, requestAttributes, nanos(100));

    Context parent2 = Context.root().with(Span.wrap(spanContext2));
    Context context2 = listener.onStart(parent2, requestAttributes, nanos(150));

    listener.onEnd(context1, responseAttributes, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.request.duration")
                    .hasDescription("Duration of HTTP server requests.")
                    .hasUnit("s")
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
                                            equalTo(HttpAttributes.ERROR_TYPE, "500"),
                                            equalTo(
                                                SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(
                                                SemanticAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(SemanticAttributes.URL_SCHEME, "https"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext1.getTraceId())
                                                    .hasSpanId(spanContext1.getSpanId()))
                                        .hasBucketBoundaries(DURATION_BUCKETS))));

    listener.onEnd(context2, responseAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.request.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(0.3 /* seconds */)
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext2.getTraceId())
                                                    .hasSpanId(spanContext2.getSpanId())))));
  }

  @Test
  void collectsHttpRouteFromEndAttributes() {
    // given
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = HttpServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(SemanticAttributes.SERVER_ADDRESS, "host")
            .put(SemanticAttributes.URL_SCHEME, "https")
            .build();

    Attributes responseAttributes =
        Attributes.builder().put(SemanticAttributes.HTTP_ROUTE, "/test/{id}").build();

    Context parentContext = Context.root();

    // when
    Context context = listener.onStart(parentContext, requestAttributes, nanos(100));
    listener.onEnd(context, responseAttributes, nanos(200));

    // then
    assertThat(metricReader.collectAllMetrics())
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.request.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(0.100 /* seconds */)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.URL_SCHEME, "https"),
                                            equalTo(
                                                SemanticAttributes.HTTP_ROUTE, "/test/{id}")))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
