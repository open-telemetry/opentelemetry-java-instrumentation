/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi.exporter;

import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Properties;
import java.util.Set;

/**
 * A {@link MetricExporterFactory} acts as the bootstrap for a {@link MetricExporter}
 * implementation. An exporter must register its implementation of a {@link MetricExporterFactory}
 * through the Java SPI framework.
 *
 * @deprecated Use {@code io.opentelemetry.sdk.autoconfigure.spi.ConfigurableMetricExporterProvider}
 *     from the {@code opentelemetry-sdk-extension-autoconfigure} instead.
 */
@Deprecated
public interface MetricExporterFactory {
  /**
   * Creates an instance of a {@link MetricExporter} based on the provided configuration.
   *
   * @param config The configuration
   * @return An implementation of a {@link MetricExporter}
   */
  MetricExporter fromConfig(Properties config);

  /**
   * Returns names of metric exporters supported by this factory.
   *
   * <p>Multiple names are useful for enabling a pair of span and metric exporters using the same
   * name, while still having separate names for enabling them individually.
   *
   * @return The exporter names supported by this factory
   */
  Set<String> getNames();
}
