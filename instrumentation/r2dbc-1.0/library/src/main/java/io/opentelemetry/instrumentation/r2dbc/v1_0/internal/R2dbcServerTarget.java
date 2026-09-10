/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import javax.annotation.Nullable;

final class R2dbcServerTarget {

  @Nullable
  static DbServerTarget create(
      @Nullable String serverAddress, @Nullable Integer serverPort, @Nullable Integer defaultPort) {
    if (isUnixDomainSocket(serverAddress)) {
      return DbServerTarget.unixSocket(serverAddress);
    }
    if (serverAddress == null
        || serverAddress.indexOf('/') >= 0
        || serverAddress.indexOf('?') >= 0
        || serverAddress.indexOf('#') >= 0
        || (serverPort != null && !isValidPort(serverPort))) {
      return null;
    }

    int firstComma = serverAddress.indexOf(',');
    int userInfoEnd = serverAddress.indexOf('@');
    // user info belongs to the whole address, so it must appear once and ahead of every host
    if ((firstComma >= 0 && userInfoEnd > firstComma)
        || (userInfoEnd >= 0 && userInfoEnd != serverAddress.lastIndexOf('@'))) {
      return null;
    }

    DbServerTargetBuilder builder = builder(defaultPort);
    for (String host : stripUserInfo(serverAddress).split(",", -1)) {
      ParsedEndpoint endpoint = parseEndpoint(host, serverPort);
      if (endpoint == null) {
        return null;
      }
      builder.addEndpoint(endpoint.host, endpoint.port == null ? -1 : endpoint.port);
    }
    return builder.build();
  }

  private static boolean isUnixDomainSocket(@Nullable String serverAddress) {
    return serverAddress != null && serverAddress.startsWith("/");
  }

  private static DbServerTargetBuilder builder(@Nullable Integer defaultPort) {
    return defaultPort == null ? DbServerTarget.builder() : DbServerTarget.builder(defaultPort);
  }

  @Nullable
  private static ParsedEndpoint parseEndpoint(String serverAddress, @Nullable Integer serverPort) {
    String host = serverAddress.trim();
    if (host.startsWith("[")) {
      int closingBracket = host.indexOf(']');
      if (closingBracket < 0 || host.indexOf(']', closingBracket + 1) >= 0) {
        return null;
      }
      String bracketedHost = host.substring(1, closingBracket);
      if (bracketedHost.indexOf(':') < 0) {
        return null;
      }
      String rest = host.substring(closingBracket + 1);
      Integer port =
          rest.isEmpty() ? serverPort : parsePort(rest.startsWith(":") ? rest.substring(1) : "");
      return !rest.isEmpty() && port == null ? null : new ParsedEndpoint(bracketedHost, port);
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

  private static String stripUserInfo(String serverAddress) {
    int at = serverAddress.lastIndexOf('@');
    return at < 0 ? serverAddress : serverAddress.substring(at + 1);
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

  private static final class ParsedEndpoint {
    private final String host;
    @Nullable private final Integer port;

    private ParsedEndpoint(String host, @Nullable Integer port) {
      this.host = host;
      this.port = port;
    }
  }

  private R2dbcServerTarget() {}
}
