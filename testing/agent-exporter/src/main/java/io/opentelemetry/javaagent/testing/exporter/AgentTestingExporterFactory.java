/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.javaagent.testing.provider.TestLogRecordExporterComponentProvider;
import io.opentelemetry.javaagent.testing.provider.TestMetricExporterComponentProvider;
import io.opentelemetry.javaagent.testing.provider.TestSpanExporterComponentProvider;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AgentTestingExporterFactory {

  static final OtlpInMemorySpanExporter spanExporter = new OtlpInMemorySpanExporter();
  static final OtlpInMemoryMetricExporter metricExporter = new OtlpInMemoryMetricExporter();
  static final OtlpInMemoryLogRecordExporter logExporter = new OtlpInMemoryLogRecordExporter();

  static {
    TestSpanExporterComponentProvider.setSpanExporter(spanExporter);
    TestMetricExporterComponentProvider.setMetricExporter(metricExporter);
    TestLogRecordExporterComponentProvider.setLogRecordExporter(logExporter);
  }

  public static List<byte[]> getSpanExportRequests() {
    return spanExporter.getCollectedExportRequests();
  }

  public static List<byte[]> getMetricExportRequests() {
    AgentTestingCustomizer.metricReader.forceFlush().join(10, TimeUnit.SECONDS);
    return metricExporter.getCollectedExportRequests();
  }

  public static List<byte[]> getLogExportRequests() {
    return logExporter.getCollectedExportRequests();
  }

  public static void reset() {
    // Flush meter provider to remove any lingering measurements
    AgentTestingCustomizer.metricReader.forceFlush().join(10, TimeUnit.SECONDS);
    spanExporter.reset();
    metricExporter.reset();
    logExporter.reset();
  }

  public static boolean forceFlushCalled() {
    return AgentTestingCustomizer.spanProcessor.forceFlushCalled;
  }

  private AgentTestingExporterFactory() {}
}
