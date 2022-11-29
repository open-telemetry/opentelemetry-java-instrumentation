/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp;

import io.opentelemetry.api.OpenTelemetry;
import org.vibur.dbcp.ViburDBCPDataSource;

/** Entrypoint for instrumenting Vibur database connection pools. */
public final class ViburTelemetry {

  /** Returns a new {@link ViburTelemetry} configured with the given {@link OpenTelemetry}. */
  public static ViburTelemetry create(OpenTelemetry openTelemetry) {
    return new ViburTelemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private ViburTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Start collecting metrics for given data source. */
  public void registerMetrics(ViburDBCPDataSource dataSource) {
    ConnectionPoolMetrics.registerMetrics(openTelemetry, dataSource);
  }

  /** Stop collecting metrics for given data source. */
  public void unregisterMetrics(ViburDBCPDataSource dataSource) {
    ConnectionPoolMetrics.unregisterMetrics(dataSource);
  }
}
