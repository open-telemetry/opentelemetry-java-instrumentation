/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.logging;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

@AutoService(SpanExporterFactory.class)
public class LoggingExporterFactory implements SpanExporterFactory {
  @Override
  public SpanExporter fromConfig(Properties config) {
    return new LoggingExporter(config.getProperty("otel.exporter.logging.prefix", ""));
  }

  @Override
  public Set<String> getNames() {
    return Collections.singleton("logging");
  }
}
