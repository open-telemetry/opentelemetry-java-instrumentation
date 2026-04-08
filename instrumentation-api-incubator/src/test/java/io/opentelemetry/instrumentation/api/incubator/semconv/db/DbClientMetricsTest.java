/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

class DbClientMetricsTest {

  static final double[] DURATION_BUCKETS =
      DbClientMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @Test
  void collectsMetrics() {
    assumeTrue(emitStableDatabaseSemconv());

    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    OperationListener listener = DbClientMetrics.get().create(meterProvider.get("test"));

    Attributes operationAttributes =
        Attributes.builder()
            .put(DB_SYSTEM_NAME, "myDb")
            .put(DB_COLLECTION_NAME, "table")
            .put(DB_NAMESPACE, "potatoes")
            .put(DB_OPERATION_NAME, "SELECT")
            .put(DB_QUERY_SUMMARY, "SELECT table")
            .put(SERVER_ADDRESS, "localhost")
            .put(SERVER_PORT, 1234)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(ERROR_TYPE, "400")
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

    Context context1 = listener.onStart(parent, operationAttributes, nanos(100));

    assertThat(metricReader.collectAllMetrics()).isEmpty();

    listener.onEnd(context1, responseAttributes, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("db.client.operation.duration")
                    .hasUnit("s")
                    .hasDescription("Duration of database client operations.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(0.15 /* seconds */)
                                        .hasAttributesSatisfying(
                                            equalTo(DB_SYSTEM_NAME, "myDb"),
                                            equalTo(DB_NAMESPACE, "potatoes"),
                                            equalTo(DB_OPERATION_NAME, "SELECT"),
                                            equalTo(DB_COLLECTION_NAME, "table"),
                                            equalTo(DB_QUERY_SUMMARY, "SELECT table"),
                                            equalTo(SERVER_ADDRESS, "localhost"),
                                            equalTo(SERVER_PORT, 1234),
                                            equalTo(ERROR_TYPE, "400"),
                                            equalTo(NETWORK_PEER_ADDRESS, "1.2.3.4"),
                                            equalTo(NETWORK_PEER_PORT, 8080))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00"))
                                        .hasBucketBoundaries(DURATION_BUCKETS))));
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
