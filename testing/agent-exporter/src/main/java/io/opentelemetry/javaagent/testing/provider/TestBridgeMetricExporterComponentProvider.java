/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestBridgeMetricExporterComponentProvider
    implements ComponentProvider<MetricExporter> {

  private static final Logger logger =
      Logger.getLogger(TestBridgeMetricExporterComponentProvider.class.getName());

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
    logger.log(
        Level.INFO, "Setting logRecord exporter to {0}", metricExporter.getClass().getName());
    TestBridgeMetricExporterComponentProvider.metricExporter = metricExporter;
  }
}
