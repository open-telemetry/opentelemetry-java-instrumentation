/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.metrics;

import io.opentelemetry.api.common.Attributes;
import software.amazon.awssdk.metrics.MetricRecord;

/** Strategy interface for handling different types of metrics in the AWS SDK. */
@FunctionalInterface
public interface MetricStrategy {
  void record(MetricRecord<?> metricRecord, Attributes attributes);
}
