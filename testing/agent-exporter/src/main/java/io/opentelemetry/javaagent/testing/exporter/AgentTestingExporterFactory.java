/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@AutoService(SpanExporterFactory.class)
public class AgentTestingExporterFactory implements SpanExporterFactory {

  public static final OtlpInMemorySpanExporter exporter = new OtlpInMemorySpanExporter();

  public static List<byte[]> getExportRequests() {
    return exporter.getCollectedExportRequests();
  }

  public static void reset() {
    exporter.reset();
  }

  @Override
  public SpanExporter fromConfig(Properties config) {
    return exporter;
  }

  @Override
  public Set<String> getNames() {
    return Collections.singleton("javaagent-testing");
  }

  @Override
  public boolean disableBatchSpanProcessor() {
    return true;
  }
}
