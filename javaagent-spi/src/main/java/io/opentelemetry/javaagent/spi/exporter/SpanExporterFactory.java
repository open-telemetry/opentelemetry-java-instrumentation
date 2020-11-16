/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi.exporter;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Properties;
import java.util.Set;

/**
 * A {@link SpanExporterFactory} acts as the bootstrap for a {@link SpanExporter} implementation. An
 * exporter must register its implementation of a {@link SpanExporterFactory} through the Java SPI
 * framework.
 */
public interface SpanExporterFactory {
  /**
   * Creates an instance of a {@link SpanExporter} based on the provided configuration.
   *
   * @param config The configuration
   * @return An implementation of a {@link SpanExporter}
   */
  SpanExporter fromConfig(Properties config);

  /**
   * Returns names of span exporters supported by this factory.
   *
   * <p>Multiple names are useful for enabling a pair of span and metric exporters using the same
   * name, while still having separate names for enabling them individually.
   *
   * @return The exporter names supported by this factory
   */
  Set<String> getNames();

  /** Returns whether the batch span processor should not be used with this exporter. */
  default boolean disableBatchSpanProcessor() {
    return false;
  }
}
