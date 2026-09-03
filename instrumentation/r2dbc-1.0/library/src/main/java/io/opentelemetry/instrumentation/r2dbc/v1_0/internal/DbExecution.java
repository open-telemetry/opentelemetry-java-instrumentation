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
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.ArrayList;
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
  private static final int MAX_ENDPOINTS = 5;
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
    ConfiguredServerTarget configuredServerTarget =
        isUnixDomainSocket(serverAddress)
            ? ConfiguredServerTarget.from(DbServerTarget.unixSocket(serverAddress))
            : isServerAddressGroupCandidate(serverAddress)
                ? buildServerAddressGroup(serverAddress, serverPort, defaultPort)
                : buildServerTarget(serverAddress, serverPort, defaultPort);
    this.configuredServerAddress = configuredServerTarget.address;
    this.configuredServerPort = configuredServerTarget.port;
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

  private static boolean isUnixDomainSocket(@Nullable String serverAddress) {
    return serverAddress != null && serverAddress.startsWith("/");
  }

  private static boolean isServerAddressGroupCandidate(@Nullable String serverAddress) {
    return serverAddress != null && serverAddress.indexOf(',') >= 0;
  }

  private static ConfiguredServerTarget buildServerAddressGroup(
      @Nullable String serverAddress, @Nullable Integer serverPort, @Nullable Integer defaultPort) {
    if (serverAddress == null
        || serverAddress.indexOf('/') >= 0
        || serverAddress.indexOf('?') >= 0
        || serverAddress.indexOf('#') >= 0
        || (serverPort != null && !isValidPort(serverPort))) {
      return ConfiguredServerTarget.EMPTY;
    }

    int firstComma = serverAddress.indexOf(',');
    int userInfoEnd = serverAddress.indexOf('@');
    if (userInfoEnd > firstComma
        || (userInfoEnd >= 0 && userInfoEnd != serverAddress.lastIndexOf('@'))) {
      return ConfiguredServerTarget.EMPTY;
    }

    String hostList = stripUserInfo(serverAddress);
    String[] hosts = hostList.split(",", -1);
    List<ParsedEndpoint> endpoints = new ArrayList<>(hosts.length);
    for (String host : hosts) {
      ParsedEndpoint endpoint = parseEndpoint(host, serverPort);
      if (endpoint == null) {
        return ConfiguredServerTarget.EMPTY;
      }
      endpoints.add(endpoint);
    }
    if (defaultPort == null) {
      return buildUnknownDefaultPortGroup(endpoints);
    }

    DbServerTargetBuilder builder = DbServerTarget.builder(defaultPort).setSorted(false);
    boolean inlinePorts = false;
    for (ParsedEndpoint endpoint : endpoints) {
      builder.addEndpoint(endpoint.host, endpoint.port == null ? -1 : endpoint.port);
      inlinePorts |= endpoint.port != null && !defaultPort.equals(endpoint.port);
    }
    ConfiguredServerTarget target = ConfiguredServerTarget.from(builder.build());
    if (!inlinePorts && target.address != null) {
      target = new ConfiguredServerTarget(bracketIpv6Endpoints(target.address), null);
    }
    return target;
  }

  private static String stripUserInfo(String serverAddress) {
    int at = serverAddress.lastIndexOf('@');
    return at < 0 ? serverAddress : serverAddress.substring(at + 1);
  }

  private static ConfiguredServerTarget buildServerTarget(
      @Nullable String serverAddress, @Nullable Integer serverPort, @Nullable Integer defaultPort) {
    if (serverAddress != null
        && (serverAddress.indexOf('/') >= 0
            || serverAddress.indexOf('?') >= 0
            || serverAddress.indexOf('#') >= 0)) {
      return ConfiguredServerTarget.EMPTY;
    }
    if (serverPort != null && !isValidPort(serverPort)) {
      return ConfiguredServerTarget.EMPTY;
    }
    ParsedEndpoint endpoint = parseEndpoint(serverAddress, serverPort);
    if (endpoint == null) {
      return ConfiguredServerTarget.EMPTY;
    }
    int effectiveDefaultPort = defaultPort != null ? defaultPort : endpoint.port == null ? 1 : -1;
    return ConfiguredServerTarget.from(
        DbServerTarget.builder(effectiveDefaultPort)
            .setSorted(false)
            .addEndpoint(endpoint.host, endpoint.port == null ? -1 : endpoint.port)
            .build());
  }

  private static ConfiguredServerTarget buildUnknownDefaultPortGroup(
      List<ParsedEndpoint> endpoints) {
    for (ParsedEndpoint endpoint : endpoints) {
      int validationDefaultPort = endpoint.port == null ? 1 : -1;
      if (DbServerTarget.builder(validationDefaultPort)
              .addEndpoint(endpoint.host, endpoint.port == null ? -1 : endpoint.port)
              .build()
          == null) {
        return ConfiguredServerTarget.EMPTY;
      }
    }

    StringBuilder addressGroup = new StringBuilder();
    for (int i = 0; i < Math.min(endpoints.size(), MAX_ENDPOINTS); i++) {
      ParsedEndpoint endpoint = endpoints.get(i);
      if (addressGroup.length() > 0) {
        addressGroup.append(',');
      }
      appendEndpoint(addressGroup, endpoint);
    }
    return new ConfiguredServerTarget(addressGroup.toString(), null);
  }

  private static String bracketIpv6Endpoints(String addressGroup) {
    StringBuilder result = new StringBuilder();
    for (String endpoint : addressGroup.split(",", -1)) {
      if (result.length() > 0) {
        result.append(',');
      }
      if (endpoint.indexOf(':') >= 0) {
        result.append('[').append(endpoint).append(']');
      } else {
        result.append(endpoint);
      }
    }
    return result.toString();
  }

  private static void appendEndpoint(StringBuilder result, ParsedEndpoint endpoint) {
    if (endpoint.host.indexOf(':') >= 0) {
      result.append('[').append(endpoint.host).append(']');
    } else {
      result.append(endpoint.host);
    }
    if (endpoint.port != null) {
      result.append(':').append(endpoint.port);
    }
  }

  @Nullable
  private static ParsedEndpoint parseEndpoint(
      @Nullable String serverAddress, @Nullable Integer serverPort) {
    if (serverAddress == null) {
      return null;
    }
    int userInfoEnd = serverAddress.indexOf('@');
    if (userInfoEnd >= 0 && userInfoEnd != serverAddress.lastIndexOf('@')) {
      return null;
    }

    String host = stripUserInfo(serverAddress).trim();
    if (host.startsWith("[")) {
      int closingBracket = host.indexOf(']');
      if (closingBracket < 0 || host.indexOf(']', closingBracket + 1) >= 0) {
        return null;
      }
      String rest = host.substring(closingBracket + 1);
      Integer port =
          rest.isEmpty() ? serverPort : parsePort(rest.startsWith(":") ? rest.substring(1) : "");
      return !rest.isEmpty() && port == null
          ? null
          : new ParsedEndpoint(host.substring(1, closingBracket), port);
    }
    if (host.indexOf('[') >= 0 || host.indexOf(']') >= 0) {
      return null;
    }

    int firstColon = host.indexOf(':');
    int lastColon = host.lastIndexOf(':');
    if (firstColon >= 0 && firstColon == lastColon) {
      Integer port = parsePort(host.substring(firstColon + 1));
      return port == null ? null : new ParsedEndpoint(host.substring(0, firstColon), port);
    }
    return new ParsedEndpoint(host, serverPort);
  }

  @Nullable
  private static Integer parsePort(String value) {
    if (value.isEmpty()) {
      return null;
    }
    int port = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
      port = port * 10 + c - '0';
      if (port > 65535) {
        return null;
      }
    }
    return port;
  }

  private static boolean isValidPort(int port) {
    return port >= 1 && port <= 65535;
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

  private static class ParsedEndpoint {
    private final String host;
    @Nullable private final Integer port;

    private ParsedEndpoint(String host, @Nullable Integer port) {
      this.host = host;
      this.port = port;
    }
  }

  private static class ConfiguredServerTarget {
    private static final ConfiguredServerTarget EMPTY = new ConfiguredServerTarget(null, null);

    @Nullable private final String address;
    @Nullable private final Integer port;

    private static ConfiguredServerTarget from(@Nullable DbServerTarget target) {
      return target == null
          ? EMPTY
          : new ConfiguredServerTarget(target.getAddress(), target.getPort());
    }

    private ConfiguredServerTarget(@Nullable String address, @Nullable Integer port) {
      this.address = address;
      this.port = port;
    }
  }
}
