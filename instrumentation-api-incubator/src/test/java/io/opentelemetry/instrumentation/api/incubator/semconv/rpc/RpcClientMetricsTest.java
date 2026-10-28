/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RpcClientMetricsTest {

  private static final double[] DURATION_BUCKETS = {
    0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5, 10, 50, 100
  };
  private static final double[] CUSTOM_BUCKETS = {0.1, 0.2, 0.3};

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);

    OperationListener listener = RpcClientMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes1 =
        Attributes.builder()
            .put(RPC_SYSTEM_NAME, "grpc")
            .put(RPC_METHOD, "myservice.EchoService/exampleMethod")
            .put(RpcSizeAttributesExtractor.RPC_REQUEST_SIZE, 10)
            .build();

    Attributes requestAttributes2 =
        Attributes.builder()
            .put(RPC_SYSTEM_NAME, "grpc")
            .put(RPC_METHOD, "myservice.EchoService/exampleMethod")
            .build();

    Attributes responseAttributes1 =
        Attributes.builder()
            .put(SERVER_ADDRESS, "example.com")
            .put(SERVER_PORT, 8080)
            .put(ERROR_TYPE, "INTERNAL")
            .put(stringKey("rpc.status_code"), "INTERNAL")
            .put(NETWORK_TRANSPORT, "tcp")
            .put(NETWORK_TYPE, "ipv4")
            .put(RpcSizeAttributesExtractor.RPC_RESPONSE_SIZE, 20)
            .build();

    Attributes responseAttributes2 =
        Attributes.builder().put(SERVER_PORT, 8080).put(NETWORK_TRANSPORT, "tcp").build();

    Context parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "ff01020304050600ff0a0b0c0d0e0f00",
                        "090a0b0c0d0e0f00",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));
    Context context1 = listener.onStart(parent, requestAttributes1, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    Context context2 = listener.onStart(Context.root(), requestAttributes2, nanos(150));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.onEnd(context1, responseAttributes1, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasName("rpc.client.call.duration")
                    .hasUnit("s")
                    .hasDescription(
                        "Measures the duration of an outgoing Remote Procedure Call (RPC).")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(0.15)
                                        .hasBucketBoundaries(DURATION_BUCKETS)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(
                                                RPC_METHOD, "myservice.EchoService/exampleMethod"),
                                            equalTo(RPC_SYSTEM_NAME, "grpc"),
                                            equalTo(ERROR_TYPE, "INTERNAL"),
                                            equalTo(stringKey("rpc.status_code"), "INTERNAL"),
                                            equalTo(SERVER_ADDRESS, "example.com"),
                                            equalTo(SERVER_PORT, 8080))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))));

    listener.onEnd(context2, responseAttributes2, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasName("rpc.client.call.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(0.15)
                                        .hasBucketBoundaries(DURATION_BUCKETS)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(
                                                RPC_METHOD, "myservice.EchoService/exampleMethod"),
                                            equalTo(RPC_SYSTEM_NAME, "grpc"),
                                            equalTo(SERVER_PORT, 8080)))));
  }

  @Test
  void customViewOverridesBucketAdvice() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .registerView(
                InstrumentSelector.builder().setName("rpc.client.call.duration").build(),
                View.builder()
                    .setAggregation(Aggregation.explicitBucketHistogram(asList(0.1, 0.2, 0.3)))
                    .build())
            .build();
    cleanup.deferCleanup(meterProvider);

    OperationListener listener = RpcClientMetrics.get().create(meterProvider.get("test"));
    Context context = listener.onStart(Context.root(), Attributes.empty(), nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasName("rpc.client.call.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasSum(0.15)
                                        .hasBucketBoundaries(CUSTOM_BUCKETS)
                                        .hasAttributes(Attributes.empty()))));
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
