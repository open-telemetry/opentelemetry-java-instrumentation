/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import java.util.List;

public class AgentTestingExporterFactory {

  static final OtlpInMemorySpanExporter exporter = new OtlpInMemorySpanExporter();

  public static List<byte[]> getExportRequests() {
    return exporter.getCollectedExportRequests();
  }

  public static void reset() {
    exporter.reset();
  }

  public static boolean forceFlushCalled() {
    return AgentTestingSdkCustomizer.spanProcessor.forceFlushCalled;
  }
}
