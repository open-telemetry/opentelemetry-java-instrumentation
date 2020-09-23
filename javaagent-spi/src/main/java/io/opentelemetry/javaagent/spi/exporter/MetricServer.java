/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi.exporter;

import io.opentelemetry.sdk.metrics.export.MetricProducer;
import java.util.Properties;

/**
 * A {@link MetricServer} acts as the bootstrap for metric exporters that use {@link MetricProducer}
 * to consume the metrics.
 *
 * <p>Implementation of {@link MetricServer} must be registered through the Java SPI framework.
 */
public interface MetricServer {

  /**
   * Initialize the metric server that pull metric from the {@link MetricProducer}.
   *
   * @param producer The metric producer
   * @param config The configuration
   */
  void configure(MetricProducer producer, Properties config);
}
