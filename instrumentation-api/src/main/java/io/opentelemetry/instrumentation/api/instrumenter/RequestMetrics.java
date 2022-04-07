/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;

/** A factory for a {@link RequestListener} for recording metrics using a {@link Meter}. */
@FunctionalInterface
public interface RequestMetrics {
  /** Returns a {@link RequestListener} for recording metrics using the given {@link Meter}. */
  RequestListener create(Meter meter);
}
