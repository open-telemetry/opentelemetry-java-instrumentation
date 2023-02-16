/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import org.apache.commons.dbcp2.BasicDataSourceMXBean;

/** Entrypoint for instrumenting Apache DBCP database connection pools. */
public final class ApacheDbcpTelemetry {

  /** Returns a new {@link ApacheDbcpTelemetry} configured with the given {@link OpenTelemetry}. */
  public static ApacheDbcpTelemetry create(OpenTelemetry openTelemetry) {
    return new ApacheDbcpTelemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private ApacheDbcpTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Start collecting metrics for given connection pool. */
  public void registerMetrics(BasicDataSourceMXBean dataSource, String dataSourceName) {
    DataSourceMetrics.registerMetrics(openTelemetry, dataSource, dataSourceName);
  }

  /** Stop collecting metrics for given connection pool. */
  public void unregisterMetrics(BasicDataSourceMXBean dataSource) {
    DataSourceMetrics.unregisterMetrics(dataSource);
  }
}
