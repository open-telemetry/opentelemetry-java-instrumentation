/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.awssdk.metrics.MetricRecord;

/** Strategy for recording long histogram metrics from AWS SDK. */
public class LongHistogramStrategy implements MetricStrategy {
  private static final Logger logger = Logger.getLogger(LongHistogramStrategy.class.getName());
  private final LongHistogram histogram;

  public LongHistogramStrategy(Meter meter, String metricName, String description) {
    this.histogram =
        meter.histogramBuilder(metricName).setDescription(description).ofLongs().build();
  }

  @Override
  public void record(MetricRecord<?> metricRecord, Attributes attributes) {
    if (metricRecord.value() instanceof Number) {
      Number value = (Number) metricRecord.value();
      histogram.record(value.longValue(), attributes);
    } else {
      logger.log(
          Level.WARNING,
          "Invalid value type for a LongHistogram metric: {}",
          metricRecord.metric().name());
    }
  }
}
