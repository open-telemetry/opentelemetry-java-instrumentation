/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpProtocolUtil {

  public static String getProtocol(@Nullable String protocol) {
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  public static String getVersion(@Nullable String protocol) {
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return normalizeHttpVersion(protocol.substring("HTTP/".length()));
    }
    return null;
  }

  public static String normalizeHttpVersion(String version) {
    if ("2.0".equals(version)) {
      return "2";
    }

    return version;
  }

  private HttpProtocolUtil() {}
}
