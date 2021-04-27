/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;

@FunctionalInterface
public interface RequestMetricsFactory {
  RequestMetrics create(Meter meter);
}
