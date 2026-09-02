/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class VertxSqlAddressGroup {

  private static final int MAX_ENDPOINTS = 5;

  private final List<Endpoint> endpoints;
  @Nullable private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable SqlConnectOptions database) {
    return of(database, null);
  }

  @Nullable
  public static VertxSqlAddressGroup of(
      @Nullable SqlConnectOptions database, @Nullable String dbSystem) {
    Endpoint endpoint = Endpoint.from(database);
    if (endpoint == null) {
      return null;
    }
    List<Endpoint> endpoints = new ArrayList<>(1);
    endpoints.add(endpoint);
    return new VertxSqlAddressGroup(endpoints, dbSystem);
  }

  @Nullable
  public static VertxSqlAddressGroup of(@Nullable List<? extends SqlConnectOptions> databases) {
    return of(databases, null);
  }

  @Nullable
  public static VertxSqlAddressGroup of(
      @Nullable List<? extends SqlConnectOptions> databases, @Nullable String dbSystem) {
    if (databases == null || databases.isEmpty()) {
      return null;
    }
    List<Endpoint> endpoints = new ArrayList<>(databases.size());
    for (SqlConnectOptions database : databases) {
      Endpoint endpoint = Endpoint.from(database);
      if (endpoint == null) {
        return null;
      }
      endpoints.add(endpoint);
    }
    return new VertxSqlAddressGroup(endpoints, dbSystem);
  }

  private VertxSqlAddressGroup(List<Endpoint> endpoints, @Nullable String dbSystem) {
    this.endpoints = endpoints;
    Integer defaultPort = defaultPort(dbSystem);
    if (endpoints.size() == 1) {
      Endpoint endpoint = endpoints.get(0);
      if (endpoint.unixSocket) {
        address = endpoint.host;
        port = null;
      } else if (defaultPort == null) {
        StringBuilder value = new StringBuilder();
        appendHostPort(value, endpoint.host, endpoint.port);
        address = value.toString();
        port = null;
      } else {
        address = endpoint.host;
        port = endpoint.port != null && !endpoint.port.equals(defaultPort) ? endpoint.port : null;
      }
      return;
    }

    for (Endpoint endpoint : endpoints) {
      if (endpoint.unixSocket) {
        address = null;
        port = null;
        return;
      }
    }

    boolean inlinePorts = defaultPort == null;
    for (Endpoint endpoint : endpoints) {
      Integer effectivePort = endpoint.port != null ? endpoint.port : defaultPort;
      if (effectivePort != null && !effectivePort.equals(defaultPort)) {
        inlinePorts = true;
      }
    }

    StringBuilder value = new StringBuilder();
    for (int i = 0; i < endpoints.size() && i < MAX_ENDPOINTS; i++) {
      Endpoint endpoint = endpoints.get(i);
      if (i > 0) {
        value.append(',');
      }
      Integer effectivePort = endpoint.port != null ? endpoint.port : defaultPort;
      appendHostPort(value, endpoint.host, inlinePorts ? effectivePort : null);
    }
    address = value.toString();
    port = null;
  }

  public VertxSqlAddressGroup withDbSystem(@Nullable String dbSystem) {
    return new VertxSqlAddressGroup(endpoints, dbSystem);
  }

  @Nullable
  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  private static void appendHostPort(StringBuilder address, String host, @Nullable Integer port) {
    if (host.startsWith("/")) {
      address.append(host);
      return;
    }
    if (port != null && host.indexOf(':') >= 0 && !host.startsWith("[")) {
      address.append('[').append(host).append(']');
    } else {
      address.append(host);
    }
    if (port != null) {
      address.append(':').append(port);
    }
  }

  @Nullable
  private static Integer defaultPort(@Nullable String dbSystem) {
    if ("postgresql".equals(dbSystem)) {
      return 5432;
    }
    if ("mysql".equals(dbSystem)) {
      return 3306;
    }
    if ("microsoft.sql_server".equals(dbSystem)) {
      return 1433;
    }
    if ("oracle.db".equals(dbSystem)) {
      return 1521;
    }
    if ("ibm.db2".equals(dbSystem)) {
      return 50000;
    }
    return null;
  }

  private static boolean isIpv6Literal(String value) {
    String address = value;
    int zone = value.indexOf('%');
    if (zone >= 0) {
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
      address = address.substring(0, ipv4Start) + "0:0";
    }

    int compression = address.indexOf("::");
    if (compression >= 0) {
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
          if (Character.digit(value.charAt(j), 16) < 0) {
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

  private static class Endpoint {
    private final String host;
    @Nullable private final Integer port;
    private final boolean unixSocket;

    private Endpoint(String host, @Nullable Integer port, boolean unixSocket) {
      this.host = host;
      this.port = port;
      this.unixSocket = unixSocket;
    }

    @Nullable
    private static Endpoint from(@Nullable SqlConnectOptions database) {
      if (database == null || database.getHost() == null) {
        return null;
      }
      String host = database.getHost().trim();
      if (host.isEmpty()) {
        return null;
      }
      boolean unixSocket = host.startsWith("/");
      if (host.indexOf(',') >= 0
          || host.indexOf('=') >= 0
          || host.indexOf('@') >= 0
          || host.indexOf('?') >= 0
          || host.indexOf('#') >= 0
          || (!unixSocket && host.indexOf('/') >= 0)) {
        return null;
      }
      for (int i = 0; i < host.length(); i++) {
        if (Character.isWhitespace(host.charAt(i))) {
          return null;
        }
      }
      if (unixSocket) {
        return host.length() == 1 || host.indexOf('%') >= 0 ? null : new Endpoint(host, null, true);
      }

      if (host.startsWith("[")) {
        int closingBracket = host.indexOf(']');
        if (closingBracket != host.length() - 1
            || closingBracket <= 1
            || !isIpv6Literal(host.substring(1, closingBracket))) {
          return null;
        }
        host = host.substring(1, closingBracket);
      } else if (host.indexOf(']') >= 0 || host.indexOf('[') >= 0) {
        return null;
      } else if (host.indexOf(':') >= 0 && !isIpv6Literal(host)) {
        return null;
      } else if (host.indexOf('%') >= 0) {
        return null;
      }

      int configuredPort = database.getPort();
      if (configuredPort > 65535) {
        return null;
      }
      return new Endpoint(host, configuredPort > 0 ? configuredPort : null, false);
    }
  }
}
