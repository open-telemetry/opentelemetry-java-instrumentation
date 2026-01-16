/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMetricMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RpcServerMetricsTest {

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = RpcServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes1 =
        buildRequestAttributes("grpc", "myservice.EchoService", "exampleMethod", true);

    Attributes requestAttributes2 =
        buildRequestAttributes("grpc", "myservice.EchoService", "exampleMethod", false);

    Attributes responseAttributes1 =
        Attributes.builder()
            .put(ServerAttributes.SERVER_ADDRESS, "example.com")
            .put(ServerAttributes.SERVER_PORT, 8080)
            .put(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "127.0.0.1")
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(RpcSizeAttributesExtractor.RPC_RESPONSE_SIZE, 20)
            .build();

    Attributes responseAttributes2 =
        Attributes.builder()
            .put(ServerAttributes.SERVER_PORT, 8080)
            .put(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "127.0.0.1")
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
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

    Context context1 = listener.onStart(parent, requestAttributes1, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    Context context2 = listener.onStart(Context.root(), requestAttributes2, nanos(150));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.onEnd(context1, responseAttributes1, nanos(250));

    // Build expected attributes for assertions
    List<AttributeAssertion> expectedAttributes1 = new ArrayList<>();
    expectedAttributes1.add(rpcSystemAssertion("grpc"));
    expectedAttributes1.addAll(rpcMetricMethodAssertions("myservice.EchoService", "exampleMethod"));
    expectedAttributes1.add(equalTo(ServerAttributes.SERVER_ADDRESS, "example.com"));
    expectedAttributes1.add(equalTo(ServerAttributes.SERVER_PORT, 8080));
    expectedAttributes1.add(equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
    if (SemconvStability.emitOldRpcSemconv()) {
      expectedAttributes1.add(equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"));
    }

    //    double expectedDurationSum = SemconvStability.emitStableRpcSemconv() ? 0.15 : 150.0;

    // In dup mode, both old and stable duration metrics are emitted
    int expectedMetricCount =
        SemconvStability.emitOldRpcSemconv() && SemconvStability.emitStableRpcSemconv() ? 4 : 3;

    // Collect metrics once (delta reader consumes on each call)
    Collection<MetricData> metrics1 = metricReader.collectAllMetrics();
    assertThat(metrics1)
        .hasSize(expectedMetricCount)
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
                                            expectedAttributes1.toArray(new AttributeAssertion[0]))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
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
                                            expectedAttributes1.toArray(new AttributeAssertion[0]))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))));

    // Assert stable duration metric if emitting stable semconv
    if (SemconvStability.emitStableRpcSemconv()) {
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
                                              expectedAttributes1.toArray(
                                                  new AttributeAssertion[0]))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))));
    }

    // Assert old duration metric if emitting old semconv
    if (SemconvStability.emitOldRpcSemconv()) {
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
                                          .hasSum(150.0)
                                          .hasAttributesSatisfyingExactly(
                                              expectedAttributes1.toArray(
                                                  new AttributeAssertion[0]))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))));
    }

    listener.onEnd(context2, responseAttributes2, nanos(300));

    List<AttributeAssertion> expectedAttributes2 = new ArrayList<>();
    expectedAttributes2.add(rpcSystemAssertion("grpc"));
    expectedAttributes2.addAll(rpcMetricMethodAssertions("myservice.EchoService", "exampleMethod"));
    expectedAttributes2.add(equalTo(ServerAttributes.SERVER_PORT, 8080));
    expectedAttributes2.add(equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));

    // Collect metrics once (delta reader consumes on each call)
    Collection<MetricData> metrics2 = metricReader.collectAllMetrics();
    assertThat(metrics2).hasSize(expectedMetricCount);

    // Assert stable duration metric if emitting stable semconv
    if (SemconvStability.emitStableRpcSemconv()) {
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
                                              expectedAttributes2.toArray(
                                                  new AttributeAssertion[0])))));
    }

    // Assert old duration metric if emitting old semconv
    if (SemconvStability.emitOldRpcSemconv()) {
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
                                          .hasSum(150.0)
                                          .hasAttributesSatisfyingExactly(
                                              expectedAttributes2.toArray(
                                                  new AttributeAssertion[0])))));
    }
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }

  private static Attributes buildRequestAttributes(
      String system, String service, String method, boolean withSize) {
    AttributesBuilder builder = Attributes.builder();

    if (SemconvStability.emitStableRpcSemconv()) {
      builder.put(
          RpcCommonAttributesExtractor.RPC_SYSTEM_NAME,
          SemconvStability.stableRpcSystemName(system));
      builder.put(RpcCommonAttributesExtractor.RPC_METHOD, service + "/" + method);
      builder.put(RpcCommonAttributesExtractor.RPC_METHOD_ORIGINAL, method);
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      builder.put(RpcIncubatingAttributes.RPC_SYSTEM, system);
      builder.put(RpcIncubatingAttributes.RPC_SERVICE, service);
      builder.put(SemconvStability.getOldRpcMethodAttributeKey(), method);
    }

    if (withSize) {
      builder.put(RpcSizeAttributesExtractor.RPC_REQUEST_SIZE, 10);
    }

    return builder.build();
  }
}
