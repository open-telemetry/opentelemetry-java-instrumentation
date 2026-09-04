/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import javax.annotation.Nullable;

public class OpenSearchConfiguredHost {

  @Nullable
  public static DbServerTarget parse(@Nullable String host) {
    return parse(host, "");
  }

  @Nullable
  public static DbServerTarget parse(@Nullable String host, String defaultScheme) {
    if (host == null) {
      return null;
    }

    String rest = host;
    String scheme = defaultScheme;
    int schemeEnd = rest.indexOf("://");
    if (hasValidScheme(rest, schemeEnd)) {
      scheme = rest.substring(0, schemeEnd);
      rest = rest.substring(schemeEnd + 3);
    }

    // the endpoint may carry a path prefix, a query string or a fragment, none of which belong to
    // the target
    for (int i = 0; i < rest.length(); i++) {
      char c = rest.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        if (rest.indexOf('@', i) >= 0) {
          return null;
        }
        rest = rest.substring(0, i);
        break;
      }
    }

    int hostStart = rest.lastIndexOf('@') + 1;
    int portStart;
    if (hostStart < rest.length() && rest.charAt(hostStart) == '[') {
      int closingBracket = rest.indexOf(']', hostStart);
      if (closingBracket < 0) {
        return null;
      }
      if (closingBracket == rest.length() - 1) {
        portStart = -1;
      } else if (rest.charAt(closingBracket + 1) == ':') {
        portStart = closingBracket + 1;
      } else {
        return null;
      }
    } else {
      portStart = rest.indexOf(':', hostStart);
      if (portStart >= 0 && portStart != rest.lastIndexOf(':')) {
        return null;
      }
    }

    int port = -1;
    String hostName;
    if (portStart >= 0) {
      if (portStart == rest.length() - 1) {
        return null;
      }
      for (int i = portStart + 1; i < rest.length(); i++) {
        char c = rest.charAt(i);
        if (c < '0' || c > '9') {
          return null;
        }
      }
      try {
        port = Integer.parseInt(rest.substring(portStart + 1));
      } catch (NumberFormatException ignored) {
        return null;
      }
      hostName = rest.substring(hostStart, portStart);
    } else {
      hostName = rest.substring(hostStart);
    }

    return OpenSearchServerTarget.of(
        singletonList(new OpenSearchServerTarget.Endpoint(hostName, port, scheme)));
  }

  private static boolean hasValidScheme(String value, int schemeEnd) {
    if (schemeEnd <= 0 || !isAsciiLetter(value.charAt(0))) {
      return false;
    }
    for (int i = 1; i < schemeEnd; i++) {
      char c = value.charAt(i);
      if (!isAsciiLetter(c) && (c < '0' || c > '9') && c != '+' && c != '-' && c != '.') {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  private OpenSearchConfiguredHost() {}
}
