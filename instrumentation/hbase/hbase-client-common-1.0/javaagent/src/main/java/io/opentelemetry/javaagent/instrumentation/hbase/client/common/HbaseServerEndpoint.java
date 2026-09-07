/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import java.net.URI;
import java.net.URISyntaxException;
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
      if (!isIpv6Literal(endpoint.substring(1, bracket))) {
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
          if (defaultPort == null || !isIpv6Literal(endpoint)) {
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

  // TODO(#20015): Replace these helpers with DbServerEndpointUtil once #20015 merges.
  static boolean isIpv6Literal(String host) {
    int zoneStart = host.indexOf('%');
    String literal = zoneStart < 0 ? host : host.substring(0, zoneStart);
    if (zoneStart >= 0 && !isZoneId(host.substring(zoneStart + 1))) {
      return false;
    }
    for (int i = 0; i < literal.length(); i++) {
      char c = literal.charAt(i);
      if (c != ':' && c != '.' && !isAsciiHexDigit(c)) {
        return false;
      }
    }
    int ipv4Start = literal.lastIndexOf(':') + 1;
    if (literal.indexOf('.') >= 0 && !isIpv4Literal(literal.substring(ipv4Start))) {
      return false;
    }
    try {
      return new URI("db://[" + literal + "]").getHost() != null;
    } catch (URISyntaxException ignored) {
      return false;
    }
  }

  static boolean isIpv4Literal(String host) {
    int parts = 0;
    int digits = 0;
    int value = 0;
    for (int i = 0; i <= host.length(); i++) {
      char c = i == host.length() ? '.' : host.charAt(i);
      if (c == '.') {
        // A part with a leading zero reads as octal to some resolvers.
        if (digits == 0 || (digits > 1 && host.charAt(i - digits) == '0') || value > 255) {
          return false;
        }
        parts++;
        digits = 0;
        value = 0;
      } else {
        if (!isAsciiDigit(c)) {
          return false;
        }
        if (++digits > 3) {
          return false;
        }
        value = value * 10 + c - '0';
      }
    }
    return parts == 4;
  }

  private static boolean isZoneId(String zoneId) {
    if (zoneId.isEmpty()) {
      return false;
    }
    for (int i = 0; i < zoneId.length(); i++) {
      if (!isUnreserved(zoneId.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isUnreserved(char c) {
    return c == '-' || c == '.' || c == '_' || c == '~' || isAsciiLetterOrDigit(c);
  }

  private static boolean isAsciiLetterOrDigit(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isAsciiDigit(c);
  }

  private static boolean isAsciiHexDigit(char c) {
    return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || isAsciiDigit(c);
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
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
