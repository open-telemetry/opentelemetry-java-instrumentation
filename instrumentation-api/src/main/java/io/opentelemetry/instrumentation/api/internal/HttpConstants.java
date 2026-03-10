/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpConstants {

  public static final Set<String> KNOWN_METHODS =
      unmodifiableSet(
          new HashSet<>(
              asList(
                  "CONNECT", "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT", "TRACE")));

  public static final Set<String> SENSITIVE_QUERY_PARAMETERS =
      unmodifiableSet(
          new HashSet<>(asList("AWSAccessKeyId", "Signature", "sig", "X-Goog-Signature")));

  public static final String _OTHER = "_OTHER";

  @Nullable
  public static Integer portOrDefaultFromScheme(@Nullable Integer port, Supplier<String> scheme) {
    if (port != null && port > 0) {
      return port;
    }
    return defaultPortForScheme(scheme.get());
  }

  @Nullable
  public static Integer portOrDefaultFromScheme(int port, Supplier<String> scheme) {
    if (port > 0) {
      return port;
    }
    return defaultPortForScheme(scheme.get());
  }

  @Nullable
  private static Integer defaultPortForScheme(@Nullable String scheme) {
    if ("http".equals(scheme)) {
      return 80;
    }
    if ("https".equals(scheme)) {
      return 443;
    }
    return null;
  }

  private HttpConstants() {}
}
