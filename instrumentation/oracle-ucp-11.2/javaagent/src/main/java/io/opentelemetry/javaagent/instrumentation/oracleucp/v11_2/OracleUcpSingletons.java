/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolNameUtil;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.oracleucp.v11_2.OracleUcpTelemetry;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Properties;
import javax.annotation.Nullable;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;

public class OracleUcpSingletons {

  private static final String DEFAULT_POOL_NAME = "oracle-ucp";

  // Keep the metric identity across stop/start because UCP restarts the same pool instance.
  private static final VirtualField<UniversalConnectionPool, JdbcConnectionPoolMetricsInfo>
      METRICS_INFO_FIELD =
          VirtualField.find(UniversalConnectionPool.class, JdbcConnectionPoolMetricsInfo.class);

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
    JdbcConnectionPoolMetricsInfo metricsInfo = getMetricsInfo(dataSource, poolName);
    METRICS_INFO_FIELD.set(connectionPool, metricsInfo);
  }

  private static JdbcConnectionPoolMetricsInfo getMetricsInfo(
      PoolDataSource dataSource, @Nullable String poolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    return poolName == null
        ? JdbcConnectionPoolNameUtil.createMetricsInfo(dbInfo, DEFAULT_POOL_NAME)
        : JdbcConnectionPoolNameUtil.createMetricsInfoWithPoolName(dbInfo, poolName);
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

    return JdbcConnectionPoolNameUtil.dbInfo(poolNameProperties);
  }

  public static void updatePoolName(UniversalConnectionPool connectionPool) {
    JdbcConnectionPoolMetricsInfo metricsInfo = METRICS_INFO_FIELD.get(connectionPool);
    if (metricsInfo != null) {
      String poolName = connectionPool.getName();
      if (poolName != null && !poolName.isEmpty()) {
        METRICS_INFO_FIELD.set(connectionPool, metricsInfo.withPoolName(poolName));
      }
    }
  }

  public static void registerMetrics(UniversalConnectionPool connectionPool) {
    JdbcConnectionPoolMetricsInfo metricsInfo = METRICS_INFO_FIELD.get(connectionPool);
    if (metricsInfo != null) {
      telemetry()
          .registerMetrics(
              connectionPool, metricsInfo.getPoolName(), metricsInfo.getDatabaseAttributes());
      return;
    }

    telemetry().registerMetrics(connectionPool);
  }

  private OracleUcpSingletons() {}
}
