/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolNameUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.oracleucp.v11_2.OracleUcpTelemetry;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;

public class OracleUcpSingletons {

  private static final String DEFAULT_POOL_NAME = "oracle-ucp";

  // Keep the derived name across stop/start because UCP restarts the same pool instance.
  private static final VirtualField<UniversalConnectionPool, String> metricPoolNameField =
      VirtualField.find(UniversalConnectionPool.class, String.class);

  private static final OracleUcpTelemetry telemetry =
      OracleUcpTelemetry.create(GlobalOpenTelemetry.get());

  public static OracleUcpTelemetry telemetry() {
    return telemetry;
  }

  public static void capturePoolName(
      PoolDataSource dataSource, UniversalConnectionPool connectionPool) {
    DbInfo dbInfo =
        JdbcConnectionUrlParser.parse(dataSource.getURL(), dataSource.getConnectionProperties());
    metricPoolNameField.set(
        connectionPool, JdbcConnectionPoolNameUtil.poolName(dbInfo, DEFAULT_POOL_NAME));
  }

  public static void clearPoolName(UniversalConnectionPool connectionPool) {
    metricPoolNameField.set(connectionPool, null);
  }

  public static void registerMetrics(UniversalConnectionPool connectionPool) {
    String poolName = metricPoolNameField.get(connectionPool);
    if (poolName == null) {
      telemetry().registerMetrics(connectionPool);
    } else {
      telemetry().registerMetrics(connectionPool, poolName);
    }
  }

  private OracleUcpSingletons() {}
}
