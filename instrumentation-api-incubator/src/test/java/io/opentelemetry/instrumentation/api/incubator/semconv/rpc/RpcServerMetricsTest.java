/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcCommonAttributesExtractor.OLD_RPC_METHOD_CONTEXT_KEY;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcCommonAttributesExtractor.RPC_SYSTEM_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;

import io.opentelemetry.api.common.AttributeKey;
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

@SuppressWarnings("deprecation") // using deprecated semconv
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
    if (SemconvStability.emitOldRpcSemconv() && SemconvStability.emitStableRpcSemconv()) {
      parent = parent.with(OLD_RPC_METHOD_CONTEXT_KEY, "exampleMethod");
    }

    Context context1 = listener.onStart(parent, requestAttributes1, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    Context context2Root = Context.root();
    if (SemconvStability.emitOldRpcSemconv() && SemconvStability.emitStableRpcSemconv()) {
      context2Root = context2Root.with(OLD_RPC_METHOD_CONTEXT_KEY, "exampleMethod");
    }
    Context context2 = listener.onStart(context2Root, requestAttributes2, nanos(150));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.onEnd(context1, responseAttributes1, nanos(250));

    // Calculate expected metric count:
    // - dup mode: old duration + stable duration + request.size + response.size = 4
    // - old-only mode: old duration + request.size + response.size = 3
    // - stable-only mode: stable duration only = 1
    int expectedMetricCount = 1; // At minimum, we always have one duration metric
    if (SemconvStability.emitOldRpcSemconv() && SemconvStability.emitStableRpcSemconv()) {
      expectedMetricCount = 4; // Both durations + both size metrics
    } else if (SemconvStability.emitOldRpcSemconv()) {
      expectedMetricCount = 3; // Old duration + both size metrics
    }

    // Collect metrics once (delta reader consumes on each call)
    Collection<MetricData> metrics1 = metricReader.collectAllMetrics();
    assertThat(metrics1).hasSize(expectedMetricCount);

    // Build expected attributes for OLD metrics (size + old duration) - alphabetically sorted
    List<AttributeAssertion> oldMetricAttributes1 = new ArrayList<>();
    if (SemconvStability.emitOldRpcSemconv()) {
      oldMetricAttributes1.add(equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
      oldMetricAttributes1.add(equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"));
      oldMetricAttributes1.add(equalTo(RpcIncubatingAttributes.RPC_METHOD, "exampleMethod"));
      oldMetricAttributes1.add(equalTo(RPC_SERVICE, "myservice.EchoService"));
      oldMetricAttributes1.add(equalTo(RPC_SYSTEM, "grpc"));
      oldMetricAttributes1.add(equalTo(ServerAttributes.SERVER_ADDRESS, "example.com"));
      oldMetricAttributes1.add(equalTo(ServerAttributes.SERVER_PORT, 8080));
    }

    // Size metrics are only recorded in old semconv mode
    if (SemconvStability.emitOldRpcSemconv()) {
      assertThat(metrics1)
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
                                              oldMetricAttributes1.toArray(
                                                  new AttributeAssertion[0]))
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
                                              oldMetricAttributes1.toArray(
                                                  new AttributeAssertion[0]))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))));
    }

    // Assert stable duration metric if emitting stable semconv
    if (SemconvStability.emitStableRpcSemconv()) {
      // Build expected attributes for STABLE metrics - alphabetically sorted
      List<AttributeAssertion> stableMetricAttributes1 = new ArrayList<>();
      stableMetricAttributes1.add(equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
      stableMetricAttributes1.add(
          equalTo(RpcIncubatingAttributes.RPC_METHOD, "myservice.EchoService/exampleMethod"));
      stableMetricAttributes1.add(
          equalTo(
              AttributeKey.stringKey("rpc.system.name"),
              SemconvStability.stableRpcSystemName("grpc")));
      stableMetricAttributes1.add(equalTo(ServerAttributes.SERVER_ADDRESS, "example.com"));
      stableMetricAttributes1.add(equalTo(ServerAttributes.SERVER_PORT, 8080));

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
                                              stableMetricAttributes1.toArray(
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
                                              oldMetricAttributes1.toArray(
                                                  new AttributeAssertion[0]))
                                          .hasExemplarsSatisfying(
                                              exemplar ->
                                                  exemplar
                                                      .hasTraceId(
                                                          "ff01020304050600ff0a0b0c0d0e0f00")
                                                      .hasSpanId("090a0b0c0d0e0f00")))));
    }

    listener.onEnd(context2, responseAttributes2, nanos(300));

    // Collect metrics once (delta reader consumes on each call)
    // Note: metrics2 doesn't include size metrics since context2 has no size attributes
    int expectedMetricCount2 =
        SemconvStability.emitOldRpcSemconv() && SemconvStability.emitStableRpcSemconv() ? 2 : 1;
    Collection<MetricData> metrics2 = metricReader.collectAllMetrics();
    assertThat(metrics2).hasSize(expectedMetricCount2);

    // Build expected attributes for OLD metrics (no server.address or network.type for context2) -
    // alphabetically sorted
    List<AttributeAssertion> oldMetricAttributes2 = new ArrayList<>();
    if (SemconvStability.emitOldRpcSemconv()) {
      oldMetricAttributes2.add(equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
      oldMetricAttributes2.add(equalTo(RpcIncubatingAttributes.RPC_METHOD, "exampleMethod"));
      oldMetricAttributes2.add(equalTo(RPC_SERVICE, "myservice.EchoService"));
      oldMetricAttributes2.add(equalTo(RPC_SYSTEM, "grpc"));
      oldMetricAttributes2.add(equalTo(ServerAttributes.SERVER_PORT, 8080));
    }

    // Build expected attributes for STABLE metrics (no server.address for context2) -
    // alphabetically sorted
    List<AttributeAssertion> stableMetricAttributes2 = new ArrayList<>();
    if (SemconvStability.emitStableRpcSemconv()) {
      stableMetricAttributes2.add(equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"));
      stableMetricAttributes2.add(
          equalTo(RpcIncubatingAttributes.RPC_METHOD, "myservice.EchoService/exampleMethod"));
      stableMetricAttributes2.add(
          equalTo(
              AttributeKey.stringKey("rpc.system.name"),
              SemconvStability.stableRpcSystemName("grpc")));
      stableMetricAttributes2.add(equalTo(ServerAttributes.SERVER_PORT, 8080));
    }

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
                                              stableMetricAttributes2.toArray(
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
                                              oldMetricAttributes2.toArray(
                                                  new AttributeAssertion[0])))));
    }
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }

  private static Attributes buildRequestAttributes(
      String system, String service, String method, boolean withSize) {
    AttributesBuilder builder = Attributes.builder();

    if (SemconvStability.emitOldRpcSemconv()) {
      builder.put(RPC_SYSTEM, system);
      builder.put(RPC_SERVICE, service);
      if (!SemconvStability.emitStableRpcSemconv()) {
        builder.put(RPC_METHOD, method);
      }
    }

    if (SemconvStability.emitStableRpcSemconv()) {
      builder.put(RPC_SYSTEM_NAME, SemconvStability.stableRpcSystemName(system));
      builder.put(RPC_METHOD, service + "/" + method);
    }

    if (withSize) {
      builder.put(RpcSizeAttributesExtractor.RPC_REQUEST_SIZE, 10);
    }

    return builder.build();
  }
}
