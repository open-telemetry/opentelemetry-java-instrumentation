/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.metrics;

import io.opentelemetry.api.common.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.awssdk.metrics.MetricRecord;

/** A {@link MetricStrategy} that delegates to another {@link MetricStrategy} and catches any
 * exceptions that occur during the delegation. If an exception occurs, it logs a warning and
 * continues. */
public class MetricStrategyWithoutErrors implements MetricStrategy {
  private static final Logger logger =
      Logger.getLogger(MetricStrategyWithoutErrors.class.getName());

  private final MetricStrategy delegate;

  public MetricStrategyWithoutErrors(MetricStrategy delegate) {
    this.delegate = delegate;
  }

  @Override
  public void record(MetricRecord<?> metricRecord, Attributes attributes) {
    if (metricRecord == null) {
      logger.log(Level.WARNING, "Received null metric record");
      return;
    }

    try {
      delegate.record(metricRecord, attributes);
    } catch (RuntimeException e) {
      String metricName = metricRecord.metric() == null ? "null" : metricRecord.metric().name();
      logger.log(Level.WARNING, e, () -> String.format("Failed to record metric: %s", metricName));
    }
  }
}
