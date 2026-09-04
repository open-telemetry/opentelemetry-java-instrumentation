/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.annotation.Nullable;

final class HbaseServerEndpoint {

  @Nullable
  static String canonicalEndpoint(String configuredEndpoint, @Nullable Integer defaultPort) {
    String endpoint = sanitizeEndpoint(configuredEndpoint);
    if (endpoint == null) {
      return null;
    }

    String host;
    Integer port = null;
    if (endpoint.charAt(0) == '[') {
      int bracket = endpoint.indexOf(']');
      if (bracket <= 1) {
        return null;
      }
      if (!isIpv6Address(endpoint.substring(1, bracket))) {
        return null;
      }
      host = endpoint.substring(0, bracket + 1);
      if (bracket + 1 < endpoint.length()) {
        if (endpoint.charAt(bracket + 1) != ':') {
          return null;
        }
        port = parsePort(endpoint.substring(bracket + 2));
        if (port == null) {
          return null;
        }
      }
    } else {
      int colon = endpoint.indexOf(':');
      if (colon >= 0) {
        if (colon == 0) {
          return null;
        }
        if (colon != endpoint.lastIndexOf(':')) {
          if (defaultPort == null || !isIpv6Address(endpoint)) {
            return null;
          }
          host = "[" + endpoint + "]";
        } else {
          host = endpoint.substring(0, colon);
          port = parsePort(endpoint.substring(colon + 1));
          if (port == null) {
            return null;
          }
        }
      } else {
        host = endpoint;
      }
    }

    if (port == null) {
      port = defaultPort;
    }
    return port == null ? host : host + ":" + port;
  }

  @Nullable
  static String sanitizeEndpoint(String configuredEndpoint) {
    String endpoint = configuredEndpoint.replaceAll("[\\t\\n\\x0B\\f\\r]", "").trim();
    for (int i = 0; i < endpoint.length(); i++) {
      char c = endpoint.charAt(i);
      if (c == '@' || c == '/' || c == '?' || c == '#' || Character.isWhitespace(c)) {
        return null;
      }
    }
    return endpoint.isEmpty() ? null : endpoint;
  }

  static boolean isIpv6Address(String address) {
    try {
      return InetAddress.getByName(address) instanceof Inet6Address;
    } catch (UnknownHostException ignored) {
      return false;
    }
  }

  @Nullable
  static Integer parsePort(@Nullable String configuredPort) {
    if (configuredPort == null) {
      return null;
    }
    try {
      int port = Integer.parseInt(configuredPort.trim());
      return port > 0 && port <= 65535 ? port : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private HbaseServerEndpoint() {}
}
