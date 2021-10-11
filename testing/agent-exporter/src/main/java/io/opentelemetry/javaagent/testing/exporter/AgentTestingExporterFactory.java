/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import java.util.List;

public class AgentTestingExporterFactory {

  static final OtlpInMemorySpanExporter spanExporter = new OtlpInMemorySpanExporter();
  static final OtlpInMemoryMetricExporter metricExporter = new OtlpInMemoryMetricExporter();

  public static List<byte[]> getSpanExportRequests() {
    return spanExporter.getCollectedExportRequests();
  }

  public static List<byte[]> getMetricExportRequests() {
    return metricExporter.getCollectedExportRequests();
  }

  public static void reset() {
    spanExporter.reset();
    metricExporter.reset();
  }

  public static boolean forceFlushCalled() {
    return AgentTestingTracingCustomizer.spanProcessor.forceFlushCalled;
  }
}
