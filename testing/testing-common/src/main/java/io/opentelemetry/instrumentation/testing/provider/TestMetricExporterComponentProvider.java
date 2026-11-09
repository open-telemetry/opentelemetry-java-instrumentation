/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

public class TestMetricExporterComponentProvider implements ComponentProvider {

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
    return requireNonNull(metricExporter, "metricExporter must not be null");
  }

  public static void setMetricExporter(MetricExporter metricExporter) {
    TestMetricExporterComponentProvider.metricExporter = metricExporter;
  }
}
