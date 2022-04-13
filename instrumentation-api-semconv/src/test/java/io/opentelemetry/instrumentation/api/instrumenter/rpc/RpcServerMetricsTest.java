/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RpcServerMetricsTest {

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    RequestListener listener = RpcServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(SemanticAttributes.RPC_SYSTEM, "grpc")
            .put(SemanticAttributes.RPC_SERVICE, "myservice.EchoService")
            .put(SemanticAttributes.RPC_METHOD, "exampleMethod")
            .build();

    Attributes responseAttributes1 =
        Attributes.builder()
            .put(SemanticAttributes.NET_HOST_NAME, "example.com")
            .put(SemanticAttributes.NET_HOST_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_HOST_PORT, 8080)
            .put(SemanticAttributes.NET_TRANSPORT, "ip_tcp")
            .build();

    Attributes responseAttributes2 =
        Attributes.builder()
            .put(SemanticAttributes.NET_HOST_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_HOST_PORT, 8080)
            .put(SemanticAttributes.NET_TRANSPORT, "ip_tcp")
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

    Context context1 = listener.start(parent, requestAttributes, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    Context context2 = listener.start(Context.root(), requestAttributes, nanos(150));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.end(context1, responseAttributes1, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(1)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("rpc.server.duration")
                    .hasUnit("ms")
                    .hasDoubleHistogram()
                    .points()
                    .satisfiesExactly(
                        point -> {
                          assertThat(point)
                              .hasSum(150 /* millis */)
                              .attributes()
                              .containsOnly(
                                  attributeEntry("rpc.system", "grpc"),
                                  attributeEntry("rpc.service", "myservice.EchoService"),
                                  attributeEntry("rpc.method", "exampleMethod"),
                                  attributeEntry("net.host.name", "example.com"),
                                  attributeEntry("net.transport", "ip_tcp"));
                          assertThat(point).exemplars().hasSize(1);
                          assertThat(point.getExemplars().get(0))
                              .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                              .hasSpanId("090a0b0c0d0e0f00");
                        }));

    listener.end(context2, responseAttributes2, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(1)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("rpc.server.duration")
                    .hasUnit("ms")
                    .hasDoubleHistogram()
                    .points()
                    .satisfiesExactly(
                        point -> {
                          assertThat(point)
                              .hasSum(150 /* millis */)
                              .attributes()
                              .containsOnly(
                                  attributeEntry("rpc.system", "grpc"),
                                  attributeEntry("rpc.service", "myservice.EchoService"),
                                  attributeEntry("rpc.method", "exampleMethod"),
                                  attributeEntry("net.host.ip", "127.0.0.1"),
                                  attributeEntry("net.transport", "ip_tcp"));
                        }));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
