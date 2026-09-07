/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utilities for validating textual database server endpoints.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DbServerEndpointUtil {

  /**
   * Returns whether {@code host} is an IPv6 literal.
   *
   * <p>The value is validated as text and is never resolved as a host name.
   */
  public static boolean isIpv6Literal(String host) {
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
      // URI parses a bracketed literal without resolving any name
      return new URI("db://[" + literal + "]").getHost() != null;
    } catch (URISyntaxException ignored) {
      return false;
    }
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

  private static boolean isIpv4Literal(String host) {
    int parts = 0;
    int digits = 0;
    int value = 0;
    for (int i = 0; i <= host.length(); i++) {
      char c = i == host.length() ? '.' : host.charAt(i);
      if (c == '.') {
        // a part with a leading zero reads as octal to some resolvers
        if (digits == 0 || (digits > 1 && host.charAt(i - digits) == '0') || value > 255) {
          return false;
        }
        parts++;
        digits = 0;
        value = 0;
      } else {
        if (++digits > 3) {
          return false;
        }
        value = value * 10 + c - '0';
      }
    }
    return parts == 4;
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

  private DbServerEndpointUtil() {}
}
