/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool2.v2_0;

import io.opentelemetry.api.OpenTelemetry;

/** Entrypoint for instrumenting Apache Commons Pool 2 object pools. */
public final class CommonsPool2Telemetry {
  private final OpenTelemetry openTelemetry;

  /**
   * Returns a new {@link CommonsPool2Telemetry} configured with the given {@link OpenTelemetry}.
   */
  public static CommonsPool2Telemetry create(OpenTelemetry openTelemetry) {
    return new CommonsPool2Telemetry(openTelemetry);
  }

  private CommonsPool2Telemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Start collecting metrics for the given supported object pool. */
  public void registerMetrics(Object pool, String poolName) {
    ConnectionPoolMetrics.registerMetrics(openTelemetry, pool, poolName);
  }

  /** Stop collecting metrics for the given supported object pool. */
  public void unregisterMetrics(Object pool) {
    ConnectionPoolMetrics.unregisterMetrics(pool);
  }
}
