/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import javax.annotation.Nullable;

/**
 * Reads the target out of the endpoint string an AWS SDK 2 transport is configured with, for
 * example {@code https://search-domain.us-east-1.es.amazonaws.com}.
 */
public final class OpenSearchConfiguredHost {

  /** The target {@code host} names, or {@code null} when nothing usable is left of it. */
  @Nullable
  public static OpenSearchServerTarget parse(@Nullable String host) {
    if (host == null) {
      return null;
    }

    String rest = host;
    int schemeEnd = rest.indexOf("://");
    if (schemeEnd >= 0) {
      rest = rest.substring(schemeEnd + 3);
    }

    // the endpoint may carry a path prefix, a query string or a fragment, none of which belong to
    // the target; credentials are dropped by the endpoint itself
    for (int i = 0; i < rest.length(); i++) {
      char c = rest.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        rest = rest.substring(0, i);
        break;
      }
    }

    int port = -1;
    int portStart = rest.lastIndexOf(':');
    if (portStart >= 0 && rest.indexOf(']', portStart) < 0) {
      try {
        port = Integer.parseInt(rest.substring(portStart + 1));
        rest = rest.substring(0, portStart);
      } catch (NumberFormatException e) {
        // not a port, keep the authority as it is
      }
    }

    return OpenSearchServerTarget.of(
        singletonList(new OpenSearchServerTarget.Endpoint(rest, port)));
  }

  private OpenSearchConfiguredHost() {}
}
