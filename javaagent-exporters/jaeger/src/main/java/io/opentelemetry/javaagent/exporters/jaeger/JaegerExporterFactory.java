/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.jaeger;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Properties;

@AutoService(SpanExporterFactory.class)
public class JaegerExporterFactory implements SpanExporterFactory {

  @Override
  public SpanExporter fromConfig(Properties config) {
    return JaegerGrpcSpanExporter.builder().readProperties(config).build();
  }
}
