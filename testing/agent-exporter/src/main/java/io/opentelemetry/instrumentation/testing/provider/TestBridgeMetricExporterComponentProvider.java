/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBridgeMetricExporterComponentProvider
    implements ComponentProvider<MetricExporter> {

  private static final Logger logger =
      LoggerFactory.getLogger(TestBridgeMetricExporterComponentProvider.class);

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
    logger.info(
        "Setting TestMetricExporterComponentProvider metric exporter to {}",
        metricExporter.getClass().getName());
    TestBridgeMetricExporterComponentProvider.metricExporter = metricExporter;
  }
}
