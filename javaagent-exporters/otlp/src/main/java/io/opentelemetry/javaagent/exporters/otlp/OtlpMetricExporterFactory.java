/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.otlp;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.otlp.OtlpGrpcMetricExporter;
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

@AutoService(MetricExporterFactory.class)
public class OtlpMetricExporterFactory implements MetricExporterFactory {

  @Override
  public MetricExporter fromConfig(Properties config) {
    return OtlpGrpcMetricExporter.builder().readProperties(config).build();
  }

  @Override
  public Set<String> getNames() {
    return new HashSet<>(Arrays.asList("otlp", "otlp_metric"));
  }
}
