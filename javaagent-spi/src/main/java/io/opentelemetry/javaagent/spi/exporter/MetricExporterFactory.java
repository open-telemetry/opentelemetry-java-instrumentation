/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi.exporter;

import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Properties;

/**
 * A {@link MetricExporterFactory} acts as the bootstrap for a {@link MetricExporter}
 * implementation. An exporter must register its implementation of a {@link MetricExporterFactory}
 * through the Java SPI framework.
 */
public interface MetricExporterFactory {
  /**
   * Creates an instance of a {@link MetricExporter} based on the provided configuration.
   *
   * @param config The configuration
   * @return An implementation of a {@link MetricExporter}
   */
  MetricExporter fromConfig(Properties config);
}
