/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

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
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpServerExperimentalMetricsTest {

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener =
        HttpServerExperimentalMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(UrlAttributes.URL_SCHEME, "https")
            .put(UrlAttributes.URL_PATH, "/")
            .put(UrlAttributes.URL_QUERY, "q=a")
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0")
            .put(ServerAttributes.SERVER_ADDRESS, "localhost")
            .put(ServerAttributes.SERVER_PORT, 1234)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .put(ErrorAttributes.ERROR_TYPE, "500")
            .put(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, 100)
            .put(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, 200)
            .put(NetworkAttributes.NETWORK_PEER_ADDRESS, "1.2.3.4")
            .put(NetworkAttributes.NETWORK_PEER_PORT, 8080)
            .put(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "4.3.2.1")
            .put(NetworkAttributes.NETWORK_LOCAL_PORT, 9090)
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
                    .hasDescription("Number of active HTTP server requests.")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfying(
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(UrlAttributes.URL_SCHEME, "https"))
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
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(UrlAttributes.URL_SCHEME, "https"))
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
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(UrlAttributes.URL_SCHEME, "https"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext1.getTraceId())
                                                    .hasSpanId(spanContext1.getSpanId())))),
            metric ->
                assertThat(metric)
                    .hasName("http.server.request.body.size")
                    .hasUnit("By")
                    .hasDescription("Size of HTTP server request bodies.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(100 /* bytes */)
                                        .hasAttributesSatisfying(
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                            equalTo(ErrorAttributes.ERROR_TYPE, "500"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(UrlAttributes.URL_SCHEME, "https"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId(spanContext1.getTraceId())
                                                    .hasSpanId(spanContext1.getSpanId())))),
            metric ->
                assertThat(metric)
                    .hasName("http.server.response.body.size")
                    .hasUnit("By")
                    .hasDescription("Size of HTTP server response bodies.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(200 /* bytes */)
                                        .hasAttributesSatisfying(
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                            equalTo(ErrorAttributes.ERROR_TYPE, "500"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(UrlAttributes.URL_SCHEME, "https"))
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
                    .hasName("http.server.request.body.size")
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
                    .hasName("http.server.response.body.size")
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
