/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.otlp;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Properties;

@AutoService(SpanExporterFactory.class)
public class OtlpSpanExporterFactory implements SpanExporterFactory {

  @Override
  public SpanExporter fromConfig(Properties config) {
    return OtlpGrpcSpanExporter.builder().readProperties(config).build();
  }
}
