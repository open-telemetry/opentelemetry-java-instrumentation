/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static java.util.stream.Collectors.joining;

import io.opentelemetry.context.Context;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbExecution {
  // copied from DbAttributes.DbSystemNameValues
  private static final String POSTGRESQL = "postgresql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MYSQL = "mysql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MARIADB = "mariadb";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MICROSOFT_SQL_SERVER = "microsoft.sql_server";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String H2DATABASE = "h2database";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  // R2DBC driver identifier → stable semconv db.system.name value
  private static final Map<String, String> DRIVER_TO_SYSTEM_NAME = buildDriverToSystemName();

  private static Map<String, String> buildDriverToSystemName() {
    Map<String, String> map = new HashMap<>();
    map.put("postgresql", POSTGRESQL);
    map.put("mysql", MYSQL);
    map.put("mariadb", MARIADB);
    map.put("mssql", MICROSOFT_SQL_SERVER);
    map.put("oracle", ORACLE_DB);
    map.put("h2", H2DATABASE);
    return map;
  }

  private final String systemName;
  private final String system;
  private final String user;
  private final String namespace;
  private final String serverAddress;
  private final Integer serverPort;
  private final String connectionString;
  private final String rawQueryText;
  private final boolean parameterizedQuery;

  private Context context;

  public DbExecution(QueryExecutionInfo queryInfo, ConnectionFactoryOptions factoryOptions) {
    Connection originalConnection = queryInfo.getConnectionInfo().getOriginalConnection();
    this.system =
        originalConnection != null
            ? originalConnection
                .getMetadata()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT)
                .split(" ")[0]
            : OTHER_SQL;
    this.user = factoryOptions.hasOption(USER) ? (String) factoryOptions.getValue(USER) : null;
    this.namespace =
        factoryOptions.hasOption(DATABASE)
            ? ((String) factoryOptions.getValue(DATABASE)).toLowerCase(Locale.ROOT)
            : null;
    String driver =
        factoryOptions.hasOption(DRIVER) ? (String) factoryOptions.getValue(DRIVER) : null;
    String protocol =
        factoryOptions.hasOption(PROTOCOL) ? (String) factoryOptions.getValue(PROTOCOL) : null;
    this.systemName = resolveDbSystemName(driver, protocol);
    this.serverAddress =
        factoryOptions.hasOption(HOST) ? (String) factoryOptions.getValue(HOST) : null;
    this.serverPort =
        factoryOptions.hasOption(PORT) ? (Integer) factoryOptions.getValue(PORT) : null;
    this.connectionString =
        String.format(
            "%s%s:%s%s",
            driver != null ? driver : "",
            protocol != null ? ":" + protocol : "",
            serverAddress != null ? "//" + serverAddress : "",
            serverPort != null ? ":" + serverPort : "");
    this.rawQueryText =
        queryInfo.getQueries().stream()
            .map(QueryInfo::getQuery)
            .map(
                query ->
                    R2dbcSqlCommenterUtil.getOriginalQuery(queryInfo.getConnectionInfo(), query))
            .collect(joining(";\n"));
    this.parameterizedQuery =
        queryInfo.getQueries().stream()
            .anyMatch(queryInfo1 -> !queryInfo1.getBindingsList().isEmpty());
    R2dbcSqlCommenterUtil.clearQueries(queryInfo.getConnectionInfo());
  }

  public String getServerAddress() {
    return serverAddress;
  }

  @Nullable
  public Integer getServerPort() {
    return serverPort;
  }

  public String getSystemName() {
    return systemName;
  }

  @Deprecated // to be removed in 3.0
  public String getSystem() {
    return system;
  }

  public String getUser() {
    return user;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getConnectionString() {
    return connectionString;
  }

  public String getRawQueryText() {
    return rawQueryText;
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  private static String resolveDbSystemName(@Nullable String driver, @Nullable String protocol) {

    // Use PROTOCOL when DRIVER is "pool" (r2dbc-pool wraps the real driver in PROTOCOL),
    // otherwise use DRIVER directly.
    String rawDriver = "pool".equals(driver) && protocol != null ? protocol : driver;
    return rawDriver != null ? DRIVER_TO_SYSTEM_NAME.getOrDefault(rawDriver, OTHER_SQL) : OTHER_SQL;
  }
}
