/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
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
  private static final VirtualField<UniversalConnectionPool, Attributes> DATABASE_ATTRIBUTES_FIELD =
      VirtualField.find(UniversalConnectionPool.class, Attributes.class);

  private static final OracleUcpTelemetry telemetry =
      OracleUcpTelemetry.create(GlobalOpenTelemetry.get());

  public static OracleUcpTelemetry telemetry() {
    return telemetry;
  }

  public static void captureDatabaseInfo(
      PoolDataSource dataSource,
      UniversalConnectionPool connectionPool,
      boolean generatedPoolName) {
    DbInfo dbInfo = getDbInfo(dataSource);
    if (generatedPoolName) {
      METRIC_POOL_NAME_FIELD.set(
          connectionPool, JdbcConnectionPoolNameUtil.poolName(dbInfo, DEFAULT_POOL_NAME));
    }
    DATABASE_ATTRIBUTES_FIELD.set(
        connectionPool, JdbcConnectionPoolNameUtil.databaseAttributes(dbInfo));
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

  public static void clearPoolName(UniversalConnectionPool connectionPool) {
    METRIC_POOL_NAME_FIELD.set(connectionPool, null);
  }

  public static void registerMetrics(UniversalConnectionPool connectionPool) {
    String poolName = METRIC_POOL_NAME_FIELD.get(connectionPool);
    Attributes databaseAttributes = DATABASE_ATTRIBUTES_FIELD.get(connectionPool);
    if (databaseAttributes != null) {
      telemetry()
          .registerMetrics(
              connectionPool,
              poolName == null ? connectionPool.getName() : poolName,
              databaseAttributes);
      return;
    }

    if (poolName == null) {
      telemetry().registerMetrics(connectionPool);
      return;
    }

    telemetry().registerMetrics(connectionPool, poolName);
  }

  private OracleUcpSingletons() {}
}
