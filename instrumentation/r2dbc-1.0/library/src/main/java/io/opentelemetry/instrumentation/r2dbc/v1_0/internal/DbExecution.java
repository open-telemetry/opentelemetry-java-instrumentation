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
    ServerTarget configuredServerTarget =
        isUnixDomainSocket(serverAddress)
            ? sanitizeUnixDomainSocket(serverAddress)
            : isServerAddressGroupCandidate(serverAddress)
                ? sanitizeServerAddressGroup(
                    serverAddress,
                    serverPort,
                    resolveDefaultPort(
                        resolvedDriver,
                        resolvedProtocol,
                        factoryOptions.hasOption(SSL)
                            && Boolean.TRUE.equals(factoryOptions.getValue(SSL))))
                : sanitizeServerTarget(serverAddress, serverPort);
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

  private static ServerTarget sanitizeUnixDomainSocket(@Nullable String serverAddress) {
    if (serverAddress == null
        || serverAddress.length() == 1
        || serverAddress.indexOf(',') >= 0
        || serverAddress.indexOf('=') >= 0
        || serverAddress.indexOf('%') >= 0
        || serverAddress.indexOf('@') >= 0
        || serverAddress.indexOf('?') >= 0
        || serverAddress.indexOf('#') >= 0) {
      return ServerTarget.EMPTY;
    }
    return new ServerTarget(serverAddress, null);
  }

  private static boolean isServerAddressGroupCandidate(@Nullable String serverAddress) {
    return serverAddress != null && serverAddress.indexOf(',') >= 0;
  }

  private static ServerTarget sanitizeServerAddressGroup(
      @Nullable String serverAddress, @Nullable Integer serverPort, @Nullable Integer defaultPort) {
    if (serverAddress == null
        || serverAddress.indexOf('/') >= 0
        || serverAddress.indexOf('?') >= 0
        || serverAddress.indexOf('#') >= 0
        || (serverPort != null && !isPort(serverPort))) {
      return ServerTarget.EMPTY;
    }

    int firstComma = serverAddress.indexOf(',');
    int userInfoEnd = serverAddress.indexOf('@');
    if (userInfoEnd > firstComma
        || (userInfoEnd >= 0 && userInfoEnd != serverAddress.lastIndexOf('@'))) {
      return ServerTarget.EMPTY;
    }

    String hostList = stripUserInfo(serverAddress);
    String[] hosts = hostList.split(",", -1);

    List<ServerTarget> targets = new ArrayList<>(hosts.length);
    Integer commonPort = null;
    boolean samePort = true;
    for (String host : hosts) {
      String trimmed = host.trim();
      if (!isValidHostPort(trimmed)) {
        return ServerTarget.EMPTY;
      }
      ServerTarget target = sanitizeServerTarget(trimmed, serverPort);
      if (target.address == null) {
        return ServerTarget.EMPTY;
      }
      Integer effectivePort = target.port != null ? target.port : defaultPort;
      targets.add(new ServerTarget(target.address, effectivePort));
      if (effectivePort == null) {
        samePort = false;
      } else if (commonPort == null) {
        commonPort = effectivePort;
      } else if (!commonPort.equals(effectivePort)) {
        samePort = false;
      }
    }

    StringBuilder group = new StringBuilder();
    for (ServerTarget target : targets) {
      if (group.length() > 0) {
        group.append(',');
      }
      appendAddress(group, target.address, samePort ? null : target.port);
    }
    Integer port = samePort && !commonPort.equals(defaultPort) ? commonPort : null;
    return new ServerTarget(group.toString(), port);
  }

  private static String stripUserInfo(String serverAddress) {
    int at = serverAddress.lastIndexOf('@');
    return at < 0 ? serverAddress : serverAddress.substring(at + 1);
  }

  private static ServerTarget sanitizeServerTarget(
      @Nullable String serverAddress, @Nullable Integer serverPort) {
    if (serverAddress == null
        || serverAddress.indexOf('/') >= 0
        || serverAddress.indexOf('?') >= 0
        || serverAddress.indexOf('#') >= 0
        || (serverPort != null && !isPort(serverPort))) {
      return ServerTarget.EMPTY;
    }

    int userInfoEnd = serverAddress.indexOf('@');
    if (userInfoEnd >= 0 && userInfoEnd != serverAddress.lastIndexOf('@')) {
      return ServerTarget.EMPTY;
    }

    String host = stripUserInfo(serverAddress).trim();
    if (!isValidHostPort(host)) {
      return ServerTarget.EMPTY;
    }

    if (host.startsWith("[")) {
      int closingBracket = host.indexOf(']');
      String port = host.substring(closingBracket + 1);
      return new ServerTarget(
          host.substring(1, closingBracket),
          port.isEmpty() ? serverPort : Integer.valueOf(port.substring(1)));
    }

    int firstColon = host.indexOf(':');
    int lastColon = host.lastIndexOf(':');
    if (firstColon >= 0 && firstColon == lastColon) {
      return new ServerTarget(
          host.substring(0, firstColon), Integer.valueOf(host.substring(firstColon + 1)));
    }
    return new ServerTarget(host, serverPort);
  }

  private static void appendAddress(
      StringBuilder addressGroup, String host, @Nullable Integer port) {
    if (isUnbracketedIpv6(host)) {
      addressGroup.append('[').append(host).append(']');
    } else {
      addressGroup.append(host);
    }
    if (port != null) {
      addressGroup.append(':').append(port);
    }
  }

  private static boolean isValidHostPort(String value) {
    if (value.isEmpty()
        || value.indexOf('=') >= 0
        || value.indexOf('@') >= 0
        || value.indexOf('[') > 0) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) {
        return false;
      }
    }
    if (value.startsWith("[")) {
      int closingBracket = value.indexOf(']');
      if (closingBracket <= 1 || value.indexOf(']', closingBracket + 1) >= 0) {
        return false;
      }
      if (!isIpv6Literal(value.substring(1, closingBracket))) {
        return false;
      }
      String rest = value.substring(closingBracket + 1);
      return rest.isEmpty() || (rest.startsWith(":") && isPort(rest.substring(1)));
    }
    if (value.indexOf(']') >= 0) {
      return false;
    }
    int firstColon = value.indexOf(':');
    if (firstColon < 0) {
      return value.indexOf('%') < 0;
    }
    int lastColon = value.lastIndexOf(':');
    if (firstColon != lastColon) {
      return isIpv6Literal(value);
    }
    return value.indexOf('%') < 0 && firstColon > 0 && isPort(value.substring(firstColon + 1));
  }

  // Checks the syntax rather than resolving the value: InetAddress hands anything it cannot parse
  // as a literal to the platform name service, and that lookup blocks until the resolver answers.
  private static boolean isIpv6Literal(String value) {
    String address = value;
    int zone = value.indexOf('%');
    if (zone >= 0) {
      // a link-local address may carry a zone identifier, which is not part of the address
      if (zone == 0 || zone == value.length() - 1 || value.indexOf('%', zone + 1) >= 0) {
        return false;
      }
      address = value.substring(0, zone);
    }

    int firstDot = address.indexOf('.');
    if (firstDot >= 0) {
      int ipv4Start = address.lastIndexOf(':') + 1;
      if (firstDot < ipv4Start || !isIpv4Literal(address.substring(ipv4Start))) {
        return false;
      }
      // An embedded IPv4 address occupies the final two IPv6 groups.
      address = address.substring(0, ipv4Start) + "0:0";
    }

    int compression = address.indexOf("::");
    if (compression >= 0) {
      // "::" stands for the omitted groups, so it may appear at most once
      if (compression != address.lastIndexOf("::")) {
        return false;
      }
      int leftGroups = countIpv6Groups(address.substring(0, compression));
      int rightGroups = countIpv6Groups(address.substring(compression + 2));
      return leftGroups >= 0 && rightGroups >= 0 && leftGroups + rightGroups < 8;
    }
    return countIpv6Groups(address) == 8;
  }

  private static int countIpv6Groups(String value) {
    if (value.isEmpty()) {
      return 0;
    }

    int groups = 0;
    int groupStart = 0;
    for (int i = 0; i <= value.length(); i++) {
      if (i == value.length() || value.charAt(i) == ':') {
        int length = i - groupStart;
        if (length == 0 || length > 4) {
          return -1;
        }
        for (int j = groupStart; j < i; j++) {
          if (!isHexDigit(value.charAt(j))) {
            return -1;
          }
        }
        groups++;
        groupStart = i + 1;
      }
    }
    return groups;
  }

  private static boolean isIpv4Literal(String value) {
    int octets = 0;
    int octetStart = 0;
    for (int i = 0; i <= value.length(); i++) {
      if (i == value.length() || value.charAt(i) == '.') {
        int length = i - octetStart;
        if (length == 0 || length > 3) {
          return false;
        }
        int octet = 0;
        for (int j = octetStart; j < i; j++) {
          char c = value.charAt(j);
          if (c < '0' || c > '9') {
            return false;
          }
          octet = octet * 10 + c - '0';
        }
        if (octet > 255) {
          return false;
        }
        octets++;
        octetStart = i + 1;
      }
    }
    return octets == 4;
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean isPort(String value) {
    if (value.isEmpty()) {
      return false;
    }
    int port = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
      port = port * 10 + c - '0';
      if (!isPort(port)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isPort(int value) {
    return value >= 0 && value <= 65535;
  }

  private static boolean isUnbracketedIpv6(String host) {
    return !host.startsWith("[") && host.indexOf(':') != host.lastIndexOf(':');
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

  private static class ServerTarget {
    private static final ServerTarget EMPTY = new ServerTarget(null, null);

    @Nullable private final String address;
    @Nullable private final Integer port;

    private ServerTarget(@Nullable String address, @Nullable Integer port) {
      this.address = address;
      this.port = port;
    }
  }
}
