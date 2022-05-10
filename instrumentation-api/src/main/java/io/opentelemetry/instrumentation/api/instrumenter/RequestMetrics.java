/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;

/** A factory for creating a {@link RequestListener} instance that records request metrics. */
@FunctionalInterface
public interface RequestMetrics {

  /**
   * Returns a {@link RequestListener} that records request metrics using the given {@link Meter}.
   */
  RequestListener create(Meter meter);
}
