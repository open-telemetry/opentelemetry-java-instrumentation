/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import java.util.List;

public class AgentTestingExporterFactory {

  static final OtlpInMemoryCollector collector = new OtlpInMemoryCollector();

  static final OtlpInMemorySpanExporter spanExporter = new OtlpInMemorySpanExporter(collector);
  static final OtlpInMemoryMetricExporter metricExporter =
      new OtlpInMemoryMetricExporter(collector);
  static final OtlpInMemoryLogExporter logExporter = new OtlpInMemoryLogExporter(collector);

  public static List<byte[]> getSpanExportRequests() {
    return collector.getTraceExportRequests();
  }

  public static List<byte[]> getMetricExportRequests() {
    return collector.getMetricsExportRequests();
  }

  public static List<byte[]> getLogExportRequests() {
    return collector.getLogsExportRequests();
  }

  public static void reset() {
    collector.reset();
  }

  public static boolean forceFlushCalled() {
    return AgentTestingTracingCustomizer.spanProcessor.forceFlushCalled;
  }
}
