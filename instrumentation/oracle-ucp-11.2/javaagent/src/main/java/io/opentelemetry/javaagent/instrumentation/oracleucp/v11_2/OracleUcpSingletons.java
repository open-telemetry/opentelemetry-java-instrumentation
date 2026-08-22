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
import java.util.Properties;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;

public class OracleUcpSingletons {

  private static final String DEFAULT_POOL_NAME = "oracle-ucp";

  // Keep the derived name across stop/start because UCP restarts the same pool instance.
  private static final VirtualField<UniversalConnectionPool, String> METRIC_POOL_NAME_FIELD =
      VirtualField.find(UniversalConnectionPool.class, String.class);

  private static final OracleUcpTelemetry telemetry =
      OracleUcpTelemetry.create(GlobalOpenTelemetry.get());

  public static OracleUcpTelemetry telemetry() {
    return telemetry;
  }

  public static void capturePoolName(
      PoolDataSource dataSource, UniversalConnectionPool connectionPool) {
    METRIC_POOL_NAME_FIELD.set(connectionPool, getMetricPoolName(dataSource));
  }

  private static String getMetricPoolName(PoolDataSource dataSource) {
    String connectionUrl = dataSource.getURL();
    Properties connectionProperties = dataSource.getConnectionProperties();

    if (connectionUrl != null) {
      DbInfo dbInfo = JdbcConnectionUrlParser.parse(connectionUrl, connectionProperties);
      return JdbcConnectionPoolNameUtil.poolName(dbInfo, DEFAULT_POOL_NAME);
    }

    Properties poolNameProperties = new Properties();
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

    return JdbcConnectionPoolNameUtil.poolName(poolNameProperties, DEFAULT_POOL_NAME);
  }

  public static void clearPoolName(UniversalConnectionPool connectionPool) {
    METRIC_POOL_NAME_FIELD.set(connectionPool, null);
  }

  public static void registerMetrics(UniversalConnectionPool connectionPool) {
    String poolName = METRIC_POOL_NAME_FIELD.get(connectionPool);
    if (poolName == null) {
      telemetry().registerMetrics(connectionPool);
    } else {
      telemetry().registerMetrics(connectionPool, poolName);
    }
  }

  private OracleUcpSingletons() {}
}
