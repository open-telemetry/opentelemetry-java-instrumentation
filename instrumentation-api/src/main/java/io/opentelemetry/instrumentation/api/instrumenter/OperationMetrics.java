/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;

/** A factory for creating a {@link OperationListener} instance that records operation metrics. */
@FunctionalInterface
public interface OperationMetrics {

  /**
   * Returns a {@link OperationListener} that records operation metrics using the given {@link
   * Meter}.
   */
  OperationListener create(Meter meter);
}
