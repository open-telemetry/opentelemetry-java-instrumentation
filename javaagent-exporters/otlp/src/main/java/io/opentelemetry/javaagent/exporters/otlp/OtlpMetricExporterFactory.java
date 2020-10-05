/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.otlp;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.otlp.OtlpGrpcMetricExporter;
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Properties;

@AutoService(MetricExporterFactory.class)
public class OtlpMetricExporterFactory implements MetricExporterFactory {

  @Override
  public MetricExporter fromConfig(Properties config) {
    return OtlpGrpcMetricExporter.newBuilder().readProperties(config).build();
  }
}
