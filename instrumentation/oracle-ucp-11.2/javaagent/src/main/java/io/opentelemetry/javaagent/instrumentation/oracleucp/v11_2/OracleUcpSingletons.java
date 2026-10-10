/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.oracleucp.v11_2.OracleUcpTelemetry;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Properties;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;

public class OracleUcpSingletons {

  private static final String DEFAULT_POOL_NAME = "oracle-ucp";

  // Keep the metric identity across stop/start because UCP restarts the same pool instance.
  private static final VirtualField<UniversalConnectionPool, PoolMetricsState> METRICS_STATE_FIELD =
      VirtualField.find(UniversalConnectionPool.class, PoolMetricsState.class);

  private static final OracleUcpTelemetry telemetry =
      OracleUcpTelemetry.create(GlobalOpenTelemetry.get());

  public static OracleUcpTelemetry telemetry() {
    return telemetry;
  }

  public static void captureMetricsInfo(
      PoolDataSource dataSource,
      UniversalConnectionPool connectionPool,
      boolean generatedPoolName) {
    String poolName = null;
    if (!generatedPoolName) {
      poolName = connectionPool.getName();
      if (poolName != null && poolName.isEmpty()) {
        poolName = null;
      }
    }
    DbInfo dbInfo = getDbInfo(dataSource);
    PoolMetricsState metricsState =
        new PoolMetricsState(
            JdbcConnectionPoolMetricsUtil.poolName(dbInfo, poolName, DEFAULT_POOL_NAME),
            JdbcConnectionPoolMetricsUtil.databaseAttributes(dbInfo));
    METRICS_STATE_FIELD.set(connectionPool, metricsState);
  }

  private static DbInfo getDbInfo(PoolDataSource dataSource) {
    String connectionUrl = dataSource.getURL();
    Properties connectionProperties = dataSource.getConnectionProperties();

    if (connectionUrl != null) {
      return JdbcConnectionUrlParser.parse(connectionUrl, connectionProperties);
    }

    Properties poolNameProperties = new Properties(connectionProperties);
    poolNameProperties.putAll(connectionProperties);

    String serverName = dataSource.getServerName();
    if (serverName != null && !serverName.isEmpty()) {
      poolNameProperties.setProperty("serverName", serverName);
    }

    int portNumber = dataSource.getPortNumber();
    if (portNumber > 0) {
      poolNameProperties.setProperty("portNumber", Integer.toString(portNumber));
    }

    String databaseName = dataSource.getDatabaseName();
    if (databaseName != null && !databaseName.isEmpty()) {
      poolNameProperties.setProperty("databaseName", databaseName);
    }

    return JdbcConnectionPoolMetricsUtil.dbInfo(poolNameProperties);
  }

  public static void updatePoolName(UniversalConnectionPool connectionPool) {
    PoolMetricsState metricsState = METRICS_STATE_FIELD.get(connectionPool);
    if (metricsState != null) {
      String poolName = connectionPool.getName();
      if (poolName != null && !poolName.isEmpty()) {
        METRICS_STATE_FIELD.set(connectionPool, metricsState.withPoolName(poolName));
      }
    }
  }

  public static void registerMetrics(UniversalConnectionPool connectionPool) {
    PoolMetricsState metricsState = METRICS_STATE_FIELD.get(connectionPool);
    if (metricsState != null) {
      telemetry()
          .registerMetrics(connectionPool, metricsState.poolName, metricsState.databaseAttributes);
      return;
    }

    telemetry().registerMetrics(connectionPool);
  }

  private static final class PoolMetricsState {
    private final String poolName;
    private final Attributes databaseAttributes;

    private PoolMetricsState(String poolName, Attributes databaseAttributes) {
      this.poolName = poolName;
      this.databaseAttributes = databaseAttributes;
    }

    private PoolMetricsState withPoolName(String poolName) {
      return new PoolMetricsState(poolName, databaseAttributes);
    }
  }

  private OracleUcpSingletons() {}
}
