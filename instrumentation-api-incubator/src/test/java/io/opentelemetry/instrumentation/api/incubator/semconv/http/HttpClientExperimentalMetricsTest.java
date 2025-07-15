/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.incubating.HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.sdk.extension.incubator.metric.viewconfig.ViewConfig;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            .put(UrlAttributes.URL_SCHEME, "https")
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

    List<AttributeAssertion> bodySizeAssertion =
        new ArrayList<>(
            Arrays.asList(
                equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                equalTo(ErrorAttributes.ERROR_TYPE, "400"),
                equalTo(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
                equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2.0"),
                equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                equalTo(ServerAttributes.SERVER_PORT, 1234),
                equalTo(UrlAttributes.URL_SCHEME, "https")));
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
                                        .hasAttributesSatisfying(bodySizeAssertion)
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
                                        .hasAttributesSatisfying(bodySizeAssertion)
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

  @Test
  void collectsOverrideMetrics() throws Exception {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProviderBuilder sdkMeterProviderBuilder =
        SdkMeterProvider.builder().registerMetricReader(metricReader);
    try (InputStream inputStream = getClass().getResourceAsStream("/view.yaml")) {
      ViewConfig.registerViews(sdkMeterProviderBuilder, inputStream);
    }
    SdkMeterProvider meterProvider = sdkMeterProviderBuilder.build();

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
            .put(UrlAttributes.URL_SCHEME, "https")
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

    List<AttributeAssertion> bodySizeAssertion =
        new ArrayList<>(Arrays.asList(equalTo(ServerAttributes.SERVER_PORT, 1234)));
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
                                        .hasAttributesSatisfying(bodySizeAssertion)
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
                                        .hasAttributesSatisfying(bodySizeAssertion)
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))));
  }

  @Test
  void collectsStableMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = HttpClientMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(HTTP_REQUEST_METHOD, "GET")
            .put(URL_FULL, "https://localhost:1234/")
            .put(URL_PATH, "/")
            .put(URL_QUERY, "q=a")
            .put(SERVER_ADDRESS, "localhost")
            .put(SERVER_PORT, 1234)
            .put(URL_SCHEME, "https")
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(HTTP_RESPONSE_STATUS_CODE, 200)
            .put(ERROR_TYPE, "400")
            .put(HTTP_REQUEST_BODY_SIZE, 100)
            .put(HTTP_RESPONSE_BODY_SIZE, 200)
            .put(NETWORK_PROTOCOL_NAME, "http")
            .put(NETWORK_PROTOCOL_VERSION, "2.0")
            .put(NETWORK_PEER_ADDRESS, "1.2.3.4")
            .put(NETWORK_PEER_PORT, 8080)
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
                                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                            equalTo(ERROR_TYPE, "400"),
                                            equalTo(NETWORK_PROTOCOL_NAME, "http"),
                                            equalTo(NETWORK_PROTOCOL_VERSION, "2.0"),
                                            equalTo(SERVER_ADDRESS, "localhost"),
                                            equalTo(SERVER_PORT, 1234),
                                            equalTo(URL_SCHEME, "https"))
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
