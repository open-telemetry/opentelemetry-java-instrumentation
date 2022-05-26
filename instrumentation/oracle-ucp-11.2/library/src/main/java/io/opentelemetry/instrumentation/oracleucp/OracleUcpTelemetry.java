/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp;

import io.opentelemetry.api.OpenTelemetry;
import oracle.ucp.UniversalConnectionPool;

/** Entrypoint for instrumenting Oracle UCP database connection pools. */
public final class OracleUcpTelemetry {

  /** Returns a new {@link OracleUcpTelemetry} configured with the given {@link OpenTelemetry}. */
  public static OracleUcpTelemetry create(OpenTelemetry openTelemetry) {
    return new OracleUcpTelemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private OracleUcpTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Start collecting metrics for given connection pool. */
  public void registerMetrics(UniversalConnectionPool universalConnectionPool) {
    ConnectionPoolMetrics.registerMetrics(openTelemetry, universalConnectionPool);
  }

  /** Stop collecting metrics for given connection pool. */
  public void unregisterMetrics(UniversalConnectionPool universalConnectionPool) {
    ConnectionPoolMetrics.unregisterMetrics(universalConnectionPool);
  }
}
