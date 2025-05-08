/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.awssdk.metrics.MetricRecord;

/**
 * Records double value metrics using a DoubleHistogram.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DoubleHistogramStrategy implements MetricStrategy {
  private static final Logger logger = Logger.getLogger(DoubleHistogramStrategy.class.getName());
  private final DoubleHistogram histogram;

  public DoubleHistogramStrategy(Meter meter, String metricName, String description) {
    this.histogram = meter.histogramBuilder(metricName).setDescription(description).build();
  }

  @Override
  public void record(MetricRecord<?> metricRecord, Attributes attributes) {
    if (metricRecord.value() instanceof Double) {
      Double value = (Double) metricRecord.value();
      histogram.record(value, attributes);
    } else {
      logger.log(
          Level.WARNING,
          "Invalid value type for a DoubleHistogram metric: {}",
          metricRecord.metric().name());
    }
  }
}
