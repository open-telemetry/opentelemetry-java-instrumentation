/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolMXBean;
import org.apache.commons.pool2.impl.GenericObjectPoolMXBean;

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

  /** Start collecting metrics for the given generic object pool. */
  public void registerMetrics(GenericObjectPoolMXBean pool, String poolName) {
    CommonsPoolMetrics.registerMetrics(openTelemetry, pool, poolName);
  }

  /** Start collecting metrics for the given generic keyed object pool. */
  public void registerMetrics(GenericKeyedObjectPoolMXBean<?> pool, String poolName) {
    CommonsPoolMetrics.registerMetrics(openTelemetry, pool, poolName);
  }

  /** Stop collecting metrics for the given generic object pool. */
  public void unregisterMetrics(GenericObjectPoolMXBean pool) {
    CommonsPoolMetrics.unregisterMetrics(pool);
  }

  /** Stop collecting metrics for the given generic keyed object pool. */
  public void unregisterMetrics(GenericKeyedObjectPoolMXBean<?> pool) {
    CommonsPoolMetrics.unregisterMetrics(pool);
  }
}
