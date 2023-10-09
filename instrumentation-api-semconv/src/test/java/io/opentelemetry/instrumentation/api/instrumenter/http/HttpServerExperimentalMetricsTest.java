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
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpServerExperimentalMetricsTest {

  @Test
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener =
        HttpServerExperimentalMetrics.get().create(meterProvider.get("test"));

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

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasUnit("{requests}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                                            equalTo(SemanticAttributes.HTTP_SCHEME, "https"),
                                            equalTo(SemanticAttributes.NET_HOST_NAME, "localhost"),
                                            equalTo(SemanticAttributes.NET_HOST_PORT, 1234L))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext1.getTraceId())
                                                    .hasSpanId(spanContext1.getSpanId())))));

    Context parent2 = Context.root().with(Span.wrap(spanContext2));
    Context context2 = listener.onStart(parent2, requestAttributes, nanos(150));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(2)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                                            equalTo(SemanticAttributes.HTTP_SCHEME, "https"),
                                            equalTo(SemanticAttributes.NET_HOST_NAME, "localhost"),
                                            equalTo(SemanticAttributes.NET_HOST_PORT, 1234L))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext2.getTraceId())
                                                    .hasSpanId(spanContext2.getSpanId())))));

    listener.onEnd(context1, responseAttributes, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfying(
                                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                                            equalTo(SemanticAttributes.HTTP_SCHEME, "https"),
                                            equalTo(SemanticAttributes.NET_HOST_NAME, "localhost"),
                                            equalTo(SemanticAttributes.NET_HOST_PORT, 1234L))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext1.getTraceId())
                                                    .hasSpanId(spanContext1.getSpanId())))),
            metric ->
                assertThat(metric)
                    .hasName("http.server.request.size")
                    .hasUnit("By")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(100 /* bytes */)
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
                                                    .hasSpanId(spanContext1.getSpanId())))),
            metric ->
                assertThat(metric)
                    .hasName("http.server.response.size")
                    .hasUnit("By")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(200 /* bytes */)
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
                                                    .hasSpanId(spanContext1.getSpanId())))));

    listener.onEnd(context2, responseAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(0)
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext2.getTraceId())
                                                    .hasSpanId(spanContext2.getSpanId())))),
            metric ->
                assertThat(metric)
                    .hasName("http.server.request.size")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(200 /* bytes */)
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext2.getTraceId())
                                                    .hasSpanId(spanContext2.getSpanId())))),
            metric ->
                assertThat(metric)
                    .hasName("http.server.response.size")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(400 /* bytes */)
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext2.getTraceId())
                                                    .hasSpanId(spanContext2.getSpanId())))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
