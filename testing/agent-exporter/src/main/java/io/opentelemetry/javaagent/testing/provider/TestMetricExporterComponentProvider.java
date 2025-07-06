/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Objects;

public class TestMetricExporterComponentProvider implements ComponentProvider<MetricExporter> {

  private static MetricExporter metricExporter;

  @Override
  public Class<MetricExporter> getType() {
    return MetricExporter.class;
  }

  @Override
  public String getName() {
    return "test";
  }

  @Override
  public MetricExporter create(DeclarativeConfigProperties config) {
    return Objects.requireNonNull(metricExporter, "metricExporter must not be null");
  }

  public static void setMetricExporter(MetricExporter metricExporter) {
    TestMetricExporterComponentProvider.metricExporter = metricExporter;
  }
}
