/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.common.Attributes;

/**
 * The name and database attributes used to identify a JDBC connection pool's metrics.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JdbcConnectionPoolMetricsInfo {

  private final String poolName;
  private final Attributes databaseAttributes;

  JdbcConnectionPoolMetricsInfo(String poolName, Attributes databaseAttributes) {
    this.poolName = poolName;
    this.databaseAttributes = databaseAttributes;
  }

  public String getPoolName() {
    return poolName;
  }

  /**
   * Returns the database attributes for stable connection pool metrics. {@link
   * io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics} controls
   * whether they are emitted.
   */
  public Attributes getDatabaseAttributes() {
    return databaseAttributes;
  }

  public JdbcConnectionPoolMetricsInfo withPoolName(String poolName) {
    return new JdbcConnectionPoolMetricsInfo(poolName, databaseAttributes);
  }
}
