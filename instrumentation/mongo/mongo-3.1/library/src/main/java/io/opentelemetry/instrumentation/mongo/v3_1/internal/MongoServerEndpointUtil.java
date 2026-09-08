/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

// Temporary helper copied from PR #20015.
// Remove it after PR #20015 merges and use DbServerEndpointUtil instead.
// Migration tracked in issue #20016.
final class MongoServerEndpointUtil {

  static boolean hasUnsafeEncodedIpv6Zone(String host) {
    int zoneSeparator = host.indexOf('%');
    return zoneSeparator >= 0 && startsWithEncodedDelimiter(host.substring(zoneSeparator + 1));
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

  private MongoServerEndpointUtil() {}
}
