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
import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.Context;
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

  private static Map<String, String> buildDriverToSystemName() {
    Map<String, String> map = new HashMap<>();
    map.put("postgresql", POSTGRESQL);
    map.put("mysql", MYSQL);
    map.put("mariadb", MARIADB);
    map.put("mssql", MICROSOFT_SQL_SERVER);
    map.put("oracle", ORACLE_DB);
    map.put("db2", IBM_DB2);
    map.put("clickhouse", CLICKHOUSE);
    map.put("h2", H2DATABASE);
    return map;
  }

  private final String systemName;
  private final String system;
  @Nullable private final String user;
  @Nullable private final String namespace;
  @Nullable private final String serverAddress;
  @Nullable private final Integer serverPort;
  private final boolean serverAddressGroupCandidate;
  @Nullable private final String serverAddressGroup;
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
    this.systemName = resolveDbSystemName(driver, protocol);
    this.serverAddress =
        factoryOptions.hasOption(HOST) ? (String) factoryOptions.getValue(HOST) : null;
    this.serverPort =
        factoryOptions.hasOption(PORT) ? (Integer) factoryOptions.getValue(PORT) : null;
    this.serverAddressGroupCandidate = isServerAddressGroup(serverAddress);
    this.serverAddressGroup =
        serverAddressGroupCandidate ? sanitizeServerAddressGroup(serverAddress, serverPort) : null;
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
  public String getServerAddressGroup() {
    return serverAddressGroup;
  }

  public boolean isServerAddressGroup() {
    return serverAddressGroupCandidate;
  }

  private static boolean isServerAddressGroup(@Nullable String serverAddress) {
    return serverAddress != null
        && serverAddress.indexOf(',') >= 0
        && !serverAddress.startsWith("/");
  }

  @Nullable
  private static String sanitizeServerAddressGroup(
      @Nullable String serverAddress, @Nullable Integer serverPort) {
    if (serverAddress == null) {
      return null;
    }

    String hostList = stripUserInfo(serverAddress);
    String[] hosts = hostList.split(",", -1);
    if (hosts.length < 2) {
      return null;
    }

    StringBuilder group = new StringBuilder();
    for (String host : hosts) {
      String trimmed = host.trim();
      if (!isValidHostPort(trimmed)) {
        return null;
      }
      if (group.length() > 0) {
        group.append(',');
      }
      if (serverPort != null && isUnbracketedIpv6(trimmed)) {
        group.append('[').append(trimmed).append("]:").append(serverPort);
      } else {
        group.append(trimmed);
        if (serverPort != null && !hasPort(trimmed)) {
          group.append(':').append(serverPort);
        }
      }
    }
    return group.toString();
  }

  private static String stripUserInfo(String serverAddress) {
    int at = serverAddress.lastIndexOf('@');
    return at < 0 ? serverAddress : serverAddress.substring(at + 1);
  }

  private static boolean isValidHostPort(String value) {
    if (value.isEmpty()
        || value.indexOf('=') >= 0
        || value.indexOf('@') >= 0
        || value.indexOf('[') > 0) {
      return false;
    }
    if (value.startsWith("[")) {
      int closingBracket = value.indexOf(']');
      if (closingBracket < 0 || value.indexOf(']', closingBracket + 1) >= 0) {
        return false;
      }
      String rest = value.substring(closingBracket + 1);
      return rest.isEmpty() || (rest.startsWith(":") && isPort(rest.substring(1)));
    }
    if (value.indexOf(']') >= 0) {
      return false;
    }
    int colon = value.lastIndexOf(':');
    return colon < 0 || value.indexOf(':') != colon || isPort(value.substring(colon + 1));
  }

  private static boolean isPort(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) < '0' || value.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }

  private static boolean isUnbracketedIpv6(String host) {
    return !host.startsWith("[") && host.indexOf(':') != host.lastIndexOf(':');
  }

  private static boolean hasPort(String host) {
    return host.startsWith("[") ? host.indexOf("]:") > 0 : host.indexOf(':') >= 0;
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

  private static String resolveDbSystemName(@Nullable String driver, @Nullable String protocol) {
    // Use PROTOCOL when DRIVER is "pool" (r2dbc-pool wraps the real driver in PROTOCOL),
    // otherwise use DRIVER directly.
    String rawDriver = "pool".equals(driver) && protocol != null ? protocol : driver;
    return rawDriver != null ? DRIVER_TO_SYSTEM_NAME.getOrDefault(rawDriver, OTHER_SQL) : OTHER_SQL;
  }
}
