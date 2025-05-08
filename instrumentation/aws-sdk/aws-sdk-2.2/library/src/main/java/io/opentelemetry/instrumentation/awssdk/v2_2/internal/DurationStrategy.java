/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.awssdk.metrics.MetricRecord;

/**
 * Records duration metrics using a LongHistogram.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DurationStrategy implements MetricStrategy {
  private static final Logger logger = Logger.getLogger(DurationStrategy.class.getName());
  private final LongHistogram histogram;

  public DurationStrategy(Meter meter, String metricName, String description) {
    this.histogram =
        meter
            .histogramBuilder(metricName)
            .setDescription(description)
            .setUnit("ns")
            .ofLongs()
            .build();
  }

  @Override
  public void record(MetricRecord<?> metricRecord, Attributes attributes) {
    if (metricRecord.value() instanceof Duration) {
      Duration duration = (Duration) metricRecord.value();
      histogram.record(duration.toNanos(), attributes);
    } else {
      logger.log(
          Level.WARNING,
          "Invalid value type for duration metric: {}",
          metricRecord.metric().name());
    }
  }
}
