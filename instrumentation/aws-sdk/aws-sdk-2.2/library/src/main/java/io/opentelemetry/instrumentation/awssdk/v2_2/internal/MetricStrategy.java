package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.Attributes;
import software.amazon.awssdk.metrics.MetricRecord;

/**
 * Strategy for recording AWS SDK metrics.
 * <p>
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
@FunctionalInterface
public interface MetricStrategy {
  void record(MetricRecord<?> metricRecord, Attributes attributes);
}
