/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;

/**
 * An implementation of {@link InstrumentationTestRunner} that initializes OpenTelemetry SDK and
 * uses in-memory exporter to collect traces and metrics.
 */
public final class SpringSmokeTestRunner extends InstrumentationTestRunner {

  static InMemorySpanExporter testSpanExporter;
  static InMemoryMetricExporter testMetricExporter;
  static InMemoryLogRecordExporter testLogRecordExporter;

  static OpenTelemetry openTelemetry;

  public SpringSmokeTestRunner(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  static void resetExporters() {
    testSpanExporter = InMemorySpanExporter.create();
    testMetricExporter = InMemoryMetricExporter.create(AggregationTemporality.DELTA);
    testLogRecordExporter = InMemoryLogRecordExporter.create();
  }

  @Override
  public void beforeTestClass() {}

  @Override
  public void afterTestClass() {}

  @Override
  public void clearAllExportedData() {
    testSpanExporter.reset();
    testMetricExporter.reset();
    testLogRecordExporter.reset();
  }

  @Override
  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  @Override
  public List<SpanData> getExportedSpans() {
    return testSpanExporter.getFinishedSpanItems();
  }

  @Override
  public List<MetricData> getExportedMetrics() {
    return testMetricExporter.getFinishedMetricItems();
  }

  @Override
  public List<LogRecordData> getExportedLogRecords() {
    return testLogRecordExporter.getFinishedLogRecordItems();
  }

  @Override
  public boolean forceFlushCalled() {
    throw new UnsupportedOperationException();
  }
}
