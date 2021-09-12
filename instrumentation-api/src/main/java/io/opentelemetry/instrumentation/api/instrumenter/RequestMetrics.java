/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;

/** A factory for a {@link RequestListener} for recording metrics using a {@link Meter}. */
@FunctionalInterface
@UnstableApi
public interface RequestMetrics {
  /** Returns a {@link RequestListener} for recording metrics using the given {@link Meter}. */
  <REQUEST, RESPONSE> RequestListener<REQUEST, RESPONSE> create(Meter meter);
}
