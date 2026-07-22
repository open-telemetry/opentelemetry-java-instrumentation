/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool.v2_0;

import io.opentelemetry.api.OpenTelemetry;

/** Entrypoint for instrumenting Apache Commons Pool 2 object pools. */
public final class CommonsPoolTelemetry {
  private final OpenTelemetry openTelemetry;

  /** Returns a new {@link CommonsPoolTelemetry} configured with the given {@link OpenTelemetry}. */
  public static CommonsPoolTelemetry create(OpenTelemetry openTelemetry) {
    return new CommonsPoolTelemetry(openTelemetry);
  }

  private CommonsPoolTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Start collecting metrics for the given supported object pool. */
  public void registerMetrics(Object pool, String poolName) {
    CommonsPoolMetrics.registerMetrics(openTelemetry, pool, poolName);
  }

  /** Stop collecting metrics for the given supported object pool. */
  public void unregisterMetrics(Object pool) {
    CommonsPoolMetrics.unregisterMetrics(pool);
  }
}
