/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.otlp;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporter.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.javaagent.spi.exporter.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

@AutoService(SpanExporterFactory.class)
public class OtlpSpanExporterFactory implements SpanExporterFactory {

  @Override
  public SpanExporter fromConfig(Properties config) {
    return OtlpGrpcSpanExporter.builder().readProperties(config).build();
  }

  @Override
  public Set<String> getNames() {
    return new HashSet<>(Arrays.asList("otlp", "otlp_span"));
  }
}
