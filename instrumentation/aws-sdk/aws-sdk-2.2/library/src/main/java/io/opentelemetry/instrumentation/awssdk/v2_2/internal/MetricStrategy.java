package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.Attributes;
import software.amazon.awssdk.metrics.MetricRecord;

@FunctionalInterface
public interface MetricStrategy {
  void record(MetricRecord<?> metricRecord, Attributes attributes);
}
