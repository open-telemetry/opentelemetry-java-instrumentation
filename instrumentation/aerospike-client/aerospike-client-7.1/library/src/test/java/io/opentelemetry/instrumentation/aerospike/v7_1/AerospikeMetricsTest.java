/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.aerospike.v7_1;

import static io.opentelemetry.instrumentation.aerospike.v7_1.AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE;
import static io.opentelemetry.instrumentation.aerospike.v7_1.AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE;
import static io.opentelemetry.instrumentation.aerospike.v7_1.AerospikeSemanticAttributes.AEROSPIKE_SET_NAME;
import static io.opentelemetry.instrumentation.aerospike.v7_1.AerospikeSemanticAttributes.AEROSPIKE_STATUS;
import static io.opentelemetry.instrumentation.aerospike.v7_1.AerospikeSemanticAttributes.AEROSPIKE_USER_KEY;
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
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AerospikeMetricsTest {

  @Test
  @SuppressWarnings("deprecation")
  // until old http semconv are dropped in 2.0
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = AerospikeMetrics.get().create(meterProvider.get("test"));

    Attributes startAttributes =
        Attributes.builder()
            .put("aerospike.namespace", "test")
            .put("aerospike.set.name", "test-set")
            .put("aerospike.user.key", "data2")
            .put("db.operation", "GET")
            .put("db.system", "aerospike")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put("aerospike.error.code", 0)
            .put("aerospike.status", "SUCCESS")
            .put("net.sock.peer.port", 62573)
            .put("net.sock.peer.name", "localhost")
            .put("net.sock.peer.addr", "127.0.0.1")
            .put("aerospike.transfer.size", 6)
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

    Context context1 = listener.onStart(parent, startAttributes, nanos(100));

    Context context2 = listener.onStart(Context.root(), startAttributes, nanos(150));

    listener.onEnd(context1, endAttributes, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("aerospike.requests")
                    .hasLongSumSatisfying(
                        counter ->
                            counter.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(2)
                                        .hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.response")
                    .hasLongSumSatisfying(
                        counter ->
                            counter.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                            equalTo(AEROSPIKE_ERROR_CODE, 0),
                                            equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, 62573),
                                            equalTo(
                                                SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                                            equalTo(
                                                SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.concurrreny")
                    .hasLongSumSatisfying(
                        upDownCounter ->
                            upDownCounter.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(1)
                                        .hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.client.duration")
                    .hasUnit("ms")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(150)
                                        .hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                            equalTo(AEROSPIKE_ERROR_CODE, 0),
                                            equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, 62573),
                                            equalTo(
                                                SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                                            equalTo(
                                                SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.record.size")
                    .hasUnit("By")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(6)
                                        .hasAttributesSatisfying(
                                            equalTo(AEROSPIKE_NAMESPACE, "test"),
                                            equalTo(AEROSPIKE_SET_NAME, "test-set"),
                                            equalTo(AEROSPIKE_USER_KEY, "data2"),
                                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                                            equalTo(SemanticAttributes.DB_SYSTEM, "aerospike"),
                                            equalTo(AEROSPIKE_ERROR_CODE, 0),
                                            equalTo(AEROSPIKE_STATUS, "SUCCESS"),
                                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, 62573),
                                            equalTo(
                                                SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                                            equalTo(
                                                SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))));

    listener.onEnd(context2, endAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("aerospike.requests")
                    .hasLongSumSatisfying(
                        counter -> counter.hasPointsSatisfying(point -> point.hasValue(2))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.response")
                    .hasLongSumSatisfying(
                        counter -> counter.hasPointsSatisfying(point -> point.hasValue(2))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.concurrreny")
                    .hasLongSumSatisfying(
                        upDownCounter ->
                            upDownCounter.hasPointsSatisfying(point -> point.hasValue(0))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.client.duration")
                    .hasUnit("ms")
                    .hasHistogramSatisfying(
                        histogram -> histogram.hasPointsSatisfying(point -> point.hasSum(300))),
            metric ->
                assertThat(metric)
                    .hasName("aerospike.record.size")
                    .hasUnit("By")
                    .hasHistogramSatisfying(
                        histogram -> histogram.hasPointsSatisfying(point -> point.hasSum(12))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
