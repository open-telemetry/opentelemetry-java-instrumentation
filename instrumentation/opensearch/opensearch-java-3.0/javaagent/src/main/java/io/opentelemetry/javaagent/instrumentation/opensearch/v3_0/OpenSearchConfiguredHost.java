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

  private static final int NO_PORT = -1;
  private static final int INVALID = -2;

  @Nullable
  public static DbServerTarget parse(@Nullable String host) {
    return parse(host, "");
  }

  @Nullable
  public static DbServerTarget parse(@Nullable String host, String defaultScheme) {
    if (host == null) {
      return null;
    }

    OpenSearchServerTarget.Endpoint endpoint = parseEndpoint(host, defaultScheme);
    return endpoint == null ? null : OpenSearchServerTarget.of(singletonList(endpoint));
  }

  @Nullable
  private static OpenSearchServerTarget.Endpoint parseEndpoint(String host, String defaultScheme) {
    String rest = host;
    String scheme = defaultScheme;
    int schemeEnd = rest.indexOf("://");
    if (hasValidScheme(rest, schemeEnd)) {
      scheme = rest.substring(0, schemeEnd);
      rest = rest.substring(schemeEnd + 3);
    }

    rest = stripEndpointSuffix(rest);
    if (rest == null) {
      return null;
    }

    int hostStart = rest.lastIndexOf('@') + 1;
    int portStart = findPortStart(rest, hostStart);
    if (portStart == INVALID) {
      return null;
    }

    int port = parsePort(rest, portStart);
    if (port == INVALID) {
      return null;
    }

    String hostName =
        portStart == NO_PORT ? rest.substring(hostStart) : rest.substring(hostStart, portStart);
    return new OpenSearchServerTarget.Endpoint(hostName, port, scheme);
  }

  @Nullable
  private static String stripEndpointSuffix(String value) {
    // The endpoint may carry a path prefix, a query string or a fragment, none of which belong to
    // the target.
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        return value.indexOf('@', i) >= 0 ? null : value.substring(0, i);
      }
    }
    return value;
  }

  private static int findPortStart(String value, int hostStart) {
    if (hostStart < value.length() && value.charAt(hostStart) == '[') {
      int closingBracket = value.indexOf(']', hostStart);
      if (closingBracket < 0) {
        return INVALID;
      }
      if (closingBracket == value.length() - 1) {
        return NO_PORT;
      }
      return value.charAt(closingBracket + 1) == ':' ? closingBracket + 1 : INVALID;
    }

    int portStart = value.indexOf(':', hostStart);
    return portStart >= 0 && portStart != value.lastIndexOf(':') ? INVALID : portStart;
  }

  private static int parsePort(String value, int portStart) {
    if (portStart == NO_PORT) {
      return NO_PORT;
    }
    if (portStart == value.length() - 1) {
      return INVALID;
    }
    for (int i = portStart + 1; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return INVALID;
      }
    }
    try {
      return Integer.parseInt(value.substring(portStart + 1));
    } catch (NumberFormatException ignored) {
      return INVALID;
    }
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
