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
import static io.r2dbc.spi.ConnectionFactoryOptions.SSL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.HashMap;
import java.util.List;
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
  private static final String IBM_DB2 = "ibm.db2";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String CLICKHOUSE = "clickhouse";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String H2DATABASE = "h2database";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  // R2DBC driver identifier → stable semconv db.system.name value
  private static final Map<String, String> DRIVER_TO_SYSTEM_NAME = buildDriverToSystemName();
  private static final Map<String, Integer> DRIVER_TO_DEFAULT_PORT = buildDriverToDefaultPort();

  private static Map<String, String> buildDriverToSystemName() {
    Map<String, String> map = new HashMap<>();
    map.put(POSTGRESQL, POSTGRESQL);
    map.put(MYSQL, MYSQL);
    map.put(MARIADB, MARIADB);
    map.put("mssql", MICROSOFT_SQL_SERVER);
    map.put("oracle", ORACLE_DB);
    map.put("db2", IBM_DB2);
    map.put(CLICKHOUSE, CLICKHOUSE);
    map.put("h2", H2DATABASE);
    return map;
  }

  private static Map<String, Integer> buildDriverToDefaultPort() {
    Map<String, Integer> map = new HashMap<>();
    map.put(POSTGRESQL, 5432);
    map.put(MYSQL, 3306);
    map.put(MARIADB, 3306);
    map.put("mssql", 1433);
    map.put("oracle", 1521);
    map.put("db2", 50000);
    return map;
  }

  private final String systemName;
  private final String system;
  @Nullable private final String user;
  @Nullable private final String namespace;
  @Nullable private final String serverAddress;
  @Nullable private final Integer serverPort;
  @Nullable private final String configuredServerAddress;
  @Nullable private final Integer configuredServerPort;
  private final String connectionString;
  private final List<String> rawQueryTexts;
  @Nullable private final Long batchSize;
  private final boolean parameterizedQuery;

  @Nullable private Context context;

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
        factoryOptions.hasOption(DATABASE) ? (String) factoryOptions.getValue(DATABASE) : null;
    String driver =
        factoryOptions.hasOption(DRIVER) ? (String) factoryOptions.getValue(DRIVER) : null;
    String protocol =
        factoryOptions.hasOption(PROTOCOL) ? (String) factoryOptions.getValue(PROTOCOL) : null;
    String resolvedDriver = resolveDriver(driver, protocol);
    String resolvedProtocol = resolveProtocol(driver, protocol);
    this.systemName = resolveDbSystemName(resolvedDriver);
    this.serverAddress =
        factoryOptions.hasOption(HOST) ? (String) factoryOptions.getValue(HOST) : null;
    this.serverPort =
        factoryOptions.hasOption(PORT) ? (Integer) factoryOptions.getValue(PORT) : null;
    Integer defaultPort =
        resolveDefaultPort(
            resolvedDriver,
            resolvedProtocol,
            factoryOptions.hasOption(SSL) && Boolean.TRUE.equals(factoryOptions.getValue(SSL)));
    DbServerTarget configuredServerTarget =
        R2dbcServerTarget.create(serverAddress, serverPort, defaultPort);
    this.configuredServerAddress =
        configuredServerTarget == null ? null : configuredServerTarget.getAddress();
    this.configuredServerPort =
        configuredServerTarget == null ? null : configuredServerTarget.getPort();
    this.connectionString =
        String.format(
            "%s%s:%s%s",
            driver != null ? driver : "",
            protocol != null ? ":" + protocol : "",
            serverAddress != null ? "//" + serverAddress : "",
            serverPort != null ? ":" + serverPort : "");
    this.rawQueryTexts =
        queryInfo.getQueries().stream()
            .map(QueryInfo::getQuery)
            .map(
                query ->
                    R2dbcSqlCommenterUtil.getOriginalQuery(queryInfo.getConnectionInfo(), query))
            .collect(toList());
    int queryInfoBatchSize = queryInfo.getBatchSize();
    // r2dbc-proxy reports 0 as the default size for ordinary non-batch executions. Those still
    // have a query text; an empty Batch.execute() is represented with no query texts.
    boolean emptyBatch = rawQueryTexts.isEmpty();
    this.batchSize = queryInfoBatchSize > 1 || emptyBatch ? (long) queryInfoBatchSize : null;
    this.parameterizedQuery =
        queryInfo.getQueries().stream()
            .anyMatch(queryInfo1 -> !queryInfo1.getBindingsList().isEmpty());
    R2dbcSqlCommenterUtil.clearQueries(queryInfo.getConnectionInfo());
  }

  @Nullable
  public String getServerAddress() {
    return serverAddress;
  }

  @Nullable
  public Integer getServerPort() {
    return serverPort;
  }

  @Nullable
  public String getConfiguredServerAddress() {
    return configuredServerAddress;
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    return configuredServerPort;
  }

  public String getSystemName() {
    return systemName;
  }

  @Deprecated // to be removed in 3.0
  public String getSystem() {
    return system;
  }

  @Nullable
  public String getUser() {
    return user;
  }

  @Nullable
  public String getNamespace() {
    return namespace;
  }

  public String getConnectionString() {
    return connectionString;
  }

  public List<String> getRawQueryTexts() {
    return rawQueryTexts;
  }

  @Nullable
  public Long getBatchSize() {
    return batchSize;
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  @Nullable
  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  @Nullable
  private static String resolveDriver(@Nullable String driver, @Nullable String protocol) {
    if (!"pool".equals(driver) || protocol == null) {
      return driver;
    }
    int separator = protocol.indexOf(':');
    return separator < 0 ? protocol : protocol.substring(0, separator);
  }

  @Nullable
  private static String resolveProtocol(@Nullable String driver, @Nullable String protocol) {
    if (!"pool".equals(driver) || protocol == null) {
      return protocol;
    }
    int separator = protocol.indexOf(':');
    return separator < 0 ? null : protocol.substring(separator + 1);
  }

  @Nullable
  private static Integer resolveDefaultPort(
      @Nullable String driver, @Nullable String protocol, boolean ssl) {
    if (CLICKHOUSE.equals(driver)) {
      return resolveClickHouseDefaultPort(protocol, ssl);
    }
    if ("h2".equals(driver) && "tcp".equals(protocol)) {
      return 9092;
    }
    return DRIVER_TO_DEFAULT_PORT.get(driver);
  }

  @Nullable
  private static Integer resolveClickHouseDefaultPort(@Nullable String protocol, boolean ssl) {
    if (protocol == null || "http".equals(protocol)) {
      return ssl ? 8443 : 8123;
    }
    if ("https".equals(protocol)) {
      return 8443;
    }
    if ("native".equals(protocol) || "tcp".equals(protocol)) {
      return ssl ? 9440 : 9000;
    }
    if ("tcps".equals(protocol)) {
      return 9440;
    }
    if ("mysql".equals(protocol)) {
      return 9004;
    }
    if ("postgres".equals(protocol) || "postgresql".equals(protocol) || "pgsql".equals(protocol)) {
      return 9005;
    }
    if ("grpc".equals(protocol) || "grpcs".equals(protocol)) {
      return 9100;
    }
    return null;
  }

  private static String resolveDbSystemName(@Nullable String driver) {
    return driver != null ? DRIVER_TO_SYSTEM_NAME.getOrDefault(driver, OTHER_SQL) : OTHER_SQL;
  }
}
