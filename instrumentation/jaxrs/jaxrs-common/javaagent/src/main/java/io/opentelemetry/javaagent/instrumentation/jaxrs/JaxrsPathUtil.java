/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

import javax.annotation.Nullable;

public class JaxrsPathUtil {
  public static String normalizePath(@Nullable String path) {
    // ensure that non-empty path starts with /
    if (path == null || path.equals("/")) {
      path = "";
    } else if (!path.startsWith("/")) {
      path = "/" + path;
    }
    // remove trailing /
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return path;
  }

  private JaxrsPathUtil() {}
}
