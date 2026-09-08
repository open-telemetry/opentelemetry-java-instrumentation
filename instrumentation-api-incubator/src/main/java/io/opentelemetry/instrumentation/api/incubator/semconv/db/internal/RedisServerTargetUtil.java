/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import java.net.URI;
import java.net.URISyntaxException;

// Temporary helper copied from PR #20015.
// Remove it after PR #20015 merges and use DbServerEndpointUtil instead.
// Migration tracked in issue #20016.
final class RedisServerTargetUtil {

  static boolean isIpv6Literal(String value) {
    int zoneStart = value.indexOf('%');
    String literal = zoneStart < 0 ? value : value.substring(0, zoneStart);
    if (zoneStart >= 0 && !isZoneId(value.substring(zoneStart + 1))) {
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

  static boolean isIpv4Literal(String value) {
    int parts = 0;
    int digits = 0;
    int number = 0;
    for (int i = 0; i <= value.length(); i++) {
      char c = i == value.length() ? '.' : value.charAt(i);
      if (c == '.') {
        if (digits == 0 || (digits > 1 && value.charAt(i - digits) == '0') || number > 255) {
          return false;
        }
        parts++;
        digits = 0;
        number = 0;
      } else {
        if (!isAsciiDigit(c)) {
          return false;
        }
        if (++digits > 3) {
          return false;
        }
        number = number * 10 + c - '0';
      }
    }
    return parts == 4;
  }

  private static boolean isZoneId(String value) {
    if (value.isEmpty() || startsWithEncodedDelimiter(value)) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!isUnreserved(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean startsWithEncodedDelimiter(String value) {
    if (value.length() < 2) {
      return false;
    }
    int high = Character.digit(value.charAt(0), 16);
    int low = Character.digit(value.charAt(1), 16);
    if (high < 0 || low < 0) {
      return false;
    }
    char decoded = (char) ((high << 4) + low);
    return decoded == ':'
        || decoded == '@'
        || decoded == '/'
        || decoded == '?'
        || decoded == '#'
        || decoded == '\\'
        || decoded == '%'
        || decoded == '=';
  }

  private static boolean isUnreserved(char c) {
    return c == '-' || c == '.' || c == '_' || c == '~' || isAsciiLetterOrDigit(c);
  }

  private static boolean isAsciiHexDigit(char c) {
    return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || isAsciiDigit(c);
  }

  private static boolean isAsciiLetterOrDigit(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isAsciiDigit(c);
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private RedisServerTargetUtil() {}
}
