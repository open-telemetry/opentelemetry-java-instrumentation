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

class HttpClientExperimentalMetricsTest {

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener =
        HttpClientExperimentalMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(UrlAttributes.URL_FULL, "https://localhost:1234/")
            .put(UrlAttributes.URL_PATH, "/")
            .put(UrlAttributes.URL_QUERY, "q=a")
            .put(ServerAttributes.SERVER_ADDRESS, "localhost")
            .put(ServerAttributes.SERVER_PORT, 1234)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .put(ErrorAttributes.ERROR_TYPE, "400")
            .put(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, 100)
            .put(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, 200)
            .put(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0")
            .put(NetworkAttributes.NETWORK_PEER_ADDRESS, "1.2.3.4")
            .put(NetworkAttributes.NETWORK_PEER_PORT, 8080)
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
                    .hasName("http.client.request.body.size")
                    .hasUnit("By")
                    .hasDescription("Size of HTTP client request bodies.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(100 /* bytes */)
                                        .hasAttributesSatisfying(
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                            equalTo(ErrorAttributes.ERROR_TYPE, "400"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                            equalTo(ServerAttributes.SERVER_PORT, 1234))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))),
            metric ->
                assertThat(metric)
                    .hasName("http.client.response.body.size")
                    .hasUnit("By")
                    .hasDescription("Size of HTTP client response bodies.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(200 /* bytes */)
                                        .hasAttributesSatisfying(
                                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                                            equalTo(ErrorAttributes.ERROR_TYPE, "400"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(
                                                NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                                            equalTo(ServerAttributes.SERVER_PORT, 1234))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))));

    listener.onEnd(context2, responseAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("http.client.request.body.size")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(point -> point.hasSum(200 /* bytes */))),
            metric ->
                assertThat(metric)
                    .hasName("http.client.response.body.size")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(point -> point.hasSum(400 /* bytes */))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
