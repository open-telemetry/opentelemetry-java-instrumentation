/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

public class TestBridgeMetricExporterComponentProvider
    implements ComponentProvider<MetricExporter> {

  private static MetricExporter metricExporter;

  @Override
  public Class<MetricExporter> getType() {
    return MetricExporter.class;
  }

  @Override
  public String getName() {
    return "test_bridge";
  }

  @Override
  public MetricExporter create(DeclarativeConfigProperties config) {
    return metricExporter;
  }

  public static void setMetricExporter(MetricExporter metricExporter) {
    TestBridgeMetricExporterComponentProvider.metricExporter = metricExporter;
  }
}
