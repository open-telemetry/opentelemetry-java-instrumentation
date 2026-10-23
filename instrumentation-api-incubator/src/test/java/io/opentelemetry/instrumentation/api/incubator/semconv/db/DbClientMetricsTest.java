/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DbClientMetricsTest {

  static final double[] DURATION_BUCKETS =
      DbClientMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(strings = {"default", "custom", "omit", "success"})
  void spanAndMetricUseFinalClassification(String classification) {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    cleanup.deferCleanup(tracerProvider);
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .build();

    DbClientAttributesGetter<Map<String, String>, Void> getter =
        new DbClientAttributesExtractorTest.TestAttributesGetter() {
          @Override
          public String getErrorType(Map<String, String> request, Void response, Throwable error) {
            if (classification.equals("custom")) {
              return "CUSTOM";
            }
            if (classification.equals("omit")) {
              return null;
            }
            return error == null ? null : error.getClass().getName();
          }
        };
    Instrumenter<Map<String, String>, Void> instrumenter =
        Instrumenter.<Map<String, String>, Void>builder(openTelemetry, "test", request -> "query")
            .addAttributesExtractor(DbClientAttributesExtractor.create(getter))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter();
    Map<String, String> request = singletonMap("db.system.name", "myDb");
    Throwable error = classification.equals("success") ? null : new IllegalStateException();
    String expectedErrorType = getter.getErrorType(request, null, error);
    // Check the contract independently of the getter used by both telemetry signals.
    assertThat(expectedErrorType)
        .isEqualTo(
            classification.equals("custom")
                ? "CUSTOM"
                : classification.equals("default") ? IllegalStateException.class.getName() : null);

    Context context = instrumenter.start(Context.root(), request);
    instrumenter.end(context, request, null, error);

    assertThat(spanExporter.getFinishedSpanItems())
        .satisfiesExactly(
            span ->
                assertThat(span)
                    .hasStatusSatisfying(
                        status ->
                            status.hasCode(error == null ? StatusCode.UNSET : StatusCode.ERROR))
                    .hasAttributesSatisfyingExactly(
                        equalTo(DB_SYSTEM_NAME, "myDb"), equalTo(ERROR_TYPE, expectedErrorType)));
    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasName("db.client.operation.duration")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point.hasAttributesSatisfyingExactly(
                                        equalTo(DB_SYSTEM_NAME, "myDb"),
                                        equalTo(ERROR_TYPE, expectedErrorType)))));
  }

  @Test
  void collectsMetrics() {

    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);

    OperationListener listener = DbClientMetrics.get().create(meterProvider.get("test"));

    Attributes operationAttributes =
        Attributes.builder()
            .put(DB_SYSTEM_NAME, "myDb")
            .put(DB_COLLECTION_NAME, "table")
            .put(DB_NAMESPACE, "potatoes")
            .put(DB_OPERATION_NAME, "SELECT")
            .put(DB_QUERY_SUMMARY, "SELECT table")
            .put(SERVER_ADDRESS, "db1.example:5432,db2.example:5432")
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put(ERROR_TYPE, "400")
            .put(DB_RESPONSE_STATUS_CODE, "400")
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
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(DB_SYSTEM_NAME, "myDb"),
                                            equalTo(DB_NAMESPACE, "potatoes"),
                                            equalTo(DB_OPERATION_NAME, "SELECT"),
                                            equalTo(DB_COLLECTION_NAME, "table"),
                                            equalTo(DB_QUERY_SUMMARY, "SELECT table"),
                                            equalTo(
                                                SERVER_ADDRESS,
                                                "db1.example:5432,db2.example:5432"),
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
