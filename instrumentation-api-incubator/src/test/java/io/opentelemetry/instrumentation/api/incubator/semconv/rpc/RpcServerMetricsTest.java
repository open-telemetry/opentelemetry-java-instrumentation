/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcCommonAttributesExtractor.RPC_SYSTEM_NAME;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class RpcServerMetricsTest {

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = RpcServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes2 =
        Attributes.builder()
            .put(RPC_SYSTEM, emitOldRpcSemconv() ? "grpc" : null)
            .put(RPC_SERVICE, emitOldRpcSemconv() ? "myservice.EchoService" : null)
            .put(
                RPC_METHOD,
                emitStableRpcSemconv() ? "myservice.EchoService/exampleMethod" : "exampleMethod")
            .put(RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "grpc" : null)
            .build();

    Attributes requestAttributes1 =
        requestAttributes2.toBuilder().put(RpcSizeAttributesExtractor.RPC_REQUEST_SIZE, 10).build();

    Attributes responseAttributes1 =
        Attributes.builder()
            .put(SERVER_ADDRESS, "example.com")
            .put(SERVER_PORT, 8080)
            .put(NETWORK_LOCAL_ADDRESS, "127.0.0.1")
            .put(NETWORK_TRANSPORT, "tcp")
            .put(NETWORK_TYPE, "ipv4")
            .put(RpcSizeAttributesExtractor.RPC_RESPONSE_SIZE, 20)
            .build();

    Attributes responseAttributes2 =
        Attributes.builder()
            .put(SERVER_PORT, 8080)
            .put(NETWORK_LOCAL_ADDRESS, "127.0.0.1")
            .put(NETWORK_TRANSPORT, "tcp")
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
    Context context1 =
        listener.onStart(
            withDualEmitContextCustomizer(parent, "exampleMethod"), requestAttributes1, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    Context context2 =
        listener.onStart(
            withDualEmitContextCustomizer(Context.root(), "exampleMethod"),
            requestAttributes2,
            nanos(150));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.onEnd(context1, responseAttributes1, nanos(250));

    Collection<MetricData> metrics1 = metricReader.collectAllMetrics();

    if (emitOldRpcSemconv()) {
      assertThat(metrics1)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("rpc.server.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(150 /* millis */)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(RPC_SYSTEM, "grpc"),
                                              equalTo(RPC_SERVICE, "myservice.EchoService"),
                                              equalTo(RPC_METHOD, "exampleMethod"),
                                              equalTo(SERVER_ADDRESS, "example.com"),
                                              equalTo(SERVER_PORT, 8080),
                                              equalTo(NETWORK_TRANSPORT, "tcp"),
                                              equalTo(NETWORK_TYPE, "ipv4"))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))))
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("rpc.server.response.size")
                      .hasUnit("By")
                      .hasDescription("Measures the size of RPC response messages (uncompressed).")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(20 /* bytes */)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(RPC_SYSTEM, "grpc"),
                                              equalTo(RPC_SERVICE, "myservice.EchoService"),
                                              equalTo(RPC_METHOD, "exampleMethod"),
                                              equalTo(SERVER_ADDRESS, "example.com"),
                                              equalTo(SERVER_PORT, 8080),
                                              equalTo(NETWORK_TRANSPORT, "tcp"),
                                              equalTo(NETWORK_TYPE, "ipv4"))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))))
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("rpc.server.request.size")
                      .hasUnit("By")
                      .hasDescription("Measures the size of RPC request messages (uncompressed).")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(10 /* bytes */)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(RPC_SYSTEM, "grpc"),
                                              equalTo(RPC_SERVICE, "myservice.EchoService"),
                                              equalTo(RPC_METHOD, "exampleMethod"),
                                              equalTo(SERVER_ADDRESS, "example.com"),
                                              equalTo(SERVER_PORT, 8080),
                                              equalTo(NETWORK_TRANSPORT, "tcp"),
                                              equalTo(NETWORK_TYPE, "ipv4"))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))));
    }

    if (emitStableRpcSemconv()) {
      assertThat(metrics1)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("rpc.server.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(0.15)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(NETWORK_TRANSPORT, "tcp"),
                                              equalTo(
                                                  RPC_METHOD,
                                                  "myservice.EchoService/exampleMethod"),
                                              equalTo(RPC_SYSTEM_NAME, "grpc"),
                                              equalTo(SERVER_ADDRESS, "example.com"),
                                              equalTo(SERVER_PORT, 8080))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))));
    }

    listener.onEnd(context2, responseAttributes2, nanos(300));

    Collection<MetricData> metrics2 = metricReader.collectAllMetrics();

    if (emitOldRpcSemconv()) {
      assertThat(metrics2)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("rpc.server.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(150 /* millis */)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(RPC_SYSTEM, "grpc"),
                                              equalTo(RPC_SERVICE, "myservice.EchoService"),
                                              equalTo(RPC_METHOD, "exampleMethod"),
                                              equalTo(SERVER_PORT, 8080),
                                              equalTo(NETWORK_TRANSPORT, "tcp")))));
    }

    if (emitStableRpcSemconv()) {
      assertThat(metrics2)
          .anySatisfy(
              metric ->
                  assertThat(metric)
                      .hasName("rpc.server.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point
                                          .hasSum(0.15)
                                          .hasAttributesSatisfyingExactly(
                                              equalTo(NETWORK_TRANSPORT, "tcp"),
                                              equalTo(
                                                  RPC_METHOD,
                                                  "myservice.EchoService/exampleMethod"),
                                              equalTo(RPC_SYSTEM_NAME, "grpc"),
                                              equalTo(SERVER_PORT, 8080)))));
    }
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }

  @SuppressWarnings("deprecation")
  private static Context withDualEmitContextCustomizer(Context context, String method) {
    return RpcMetricsContextCustomizers.<String>dualEmitContextCustomizer(
            new RpcAttributesGetter<String, Void>() {
              @Override
              public String getSystem(String request) {
                return null;
              }

              @Override
              public String getService(String request) {
                return null;
              }

              @Override
              public String getMethod(String request) {
                return request;
              }
            })
        .onStart(context, method, Attributes.empty());
  }
}
