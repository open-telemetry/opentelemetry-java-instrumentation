/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.internal.aggregator.ExplicitBucketHistogramUtils;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class HttpServerMetricsTest {

  static final double[] DEFAULT_BUCKETS =
      ExplicitBucketHistogramUtils.DEFAULT_HISTOGRAM_BUCKET_BOUNDARIES.stream()
          .mapToDouble(d -> d)
          .toArray();

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = HttpServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put("http.method", "GET")
            .put("http.target", "/")
            .put("http.scheme", "https")
            .put("net.transport", IP_TCP)
            .put(SemanticAttributes.NET_PROTOCOL_NAME, "http")
            .put(SemanticAttributes.NET_PROTOCOL_VERSION, "2.0")
            .put("net.host.name", "localhost")
            .put("net.host.port", 1234)
            .put("net.sock.family", "inet")
            .put("net.sock.peer.addr", "1.2.3.4")
            .put("net.sock.peer.port", 8080)
            .put("net.sock.host.addr", "4.3.2.1")
            .put("net.sock.host.port", 9090)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put("http.status_code", 200)
            .put("http.request_content_length", 100)
            .put("http.response_content_length", 200)
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
                    .hasName("http.server.duration")
                    .hasUnit("ms")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(150 /* millis */)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200),
                                            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
                                            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "2.0"),
                                            equalTo(SemanticAttributes.HTTP_SCHEME, "https"),
                                            equalTo(SemanticAttributes.NET_HOST_NAME, "localhost"),
                                            equalTo(SemanticAttributes.NET_HOST_PORT, 1234))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext1.getTraceId())
                                                    .hasSpanId(spanContext1.getSpanId()))
                                        .hasBucketBoundaries(DEFAULT_BUCKETS))));

    listener.onEnd(context2, responseAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(300 /* millis */)
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
        Attributes.builder().put("net.host.name", "host").put("http.scheme", "https").build();

    Attributes responseAttributes = Attributes.builder().put("http.route", "/test/{id}").build();

    Context parentContext = Context.root();

    // when
    Context context = listener.onStart(parentContext, requestAttributes, nanos(100));
    listener.onEnd(context, responseAttributes, nanos(200));

    // then
    assertThat(metricReader.collectAllMetrics())
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.duration")
                    .hasUnit("ms")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(100 /* millis */)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.HTTP_SCHEME, "https"),
                                            equalTo(SemanticAttributes.NET_HOST_NAME, "host"),
                                            equalTo(
                                                SemanticAttributes.HTTP_ROUTE, "/test/{id}")))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
