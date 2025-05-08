/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.metrics.internal;

import io.opentelemetry.api.common.Attributes;
import software.amazon.awssdk.metrics.MetricRecord;

/**
 * Strategy interface for handling different types of metrics in the AWS SDK.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@FunctionalInterface
public interface MetricStrategy {
  void record(MetricRecord<?> metricRecord, Attributes attributes);
}
