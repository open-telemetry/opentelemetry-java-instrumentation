/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import org.checkerframework.checker.nullness.qual.Nullable;

// internal until decisions made on
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3700
public class UriBuilder {

  // note: currently path must be empty or start with "/" but that can be relaxed if needed
  public static String uri(
      String scheme, String host, int serverPort, String path, @Nullable String query) {

    boolean isDefaultPort =
        (scheme.equals("http") && serverPort == 80)
            || (scheme.equals("https") && serverPort == 443);

    // +3 is space for "://"
    int length = scheme.length() + 3 + host.length() + path.length();
    if (!isDefaultPort && serverPort != -1) {
      // +6 is space for ":" and max port number (65535)
      length += 6;
    }
    if (query != null) {
      // the +1 is space for "?"
      length += 1 + query.length();
    }

    StringBuilder url = new StringBuilder(length);
    url.append(scheme);
    url.append("://");
    url.append(host);
    if (!isDefaultPort && serverPort != -1) {
      url.append(':');
      url.append(serverPort);
    }
    url.append(path);
    if (query != null) {
      url.append('?');
      url.append(query);
    }
    return url.toString();
  }

  private UriBuilder() {}
}
