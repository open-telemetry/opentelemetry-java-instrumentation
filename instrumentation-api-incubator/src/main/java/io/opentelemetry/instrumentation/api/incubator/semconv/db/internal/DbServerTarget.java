/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import javax.annotation.Nullable;

/**
 * The logical server a database client was configured to talk to, rendered as {@code
 * server.address} and {@code server.port}.
 *
 * <p>A target stays the same across routing, node selection, and retries, so it is derived from
 * client configuration rather than from the endpoint that served an individual operation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DbServerTarget {

  private final String address;
  @Nullable private final Integer port;

  /**
   * Returns a builder for a target whose endpoints listen on {@code defaultPort} unless they are
   * configured otherwise.
   */
  public static DbServerTargetBuilder builder(int defaultPort) {
    return new DbServerTargetBuilder(defaultPort);
  }

  /**
   * Returns a target for an already-extracted Unix socket {@code path}, or {@code null} when it
   * cannot be represented safely.
   *
   * <p>This method does not parse URIs or connection strings. Accepted paths are preserved exactly.
   */
  @Nullable
  public static DbServerTarget unixSocket(@Nullable String path) {
    if (path == null
        || path.length() <= 1
        || path.charAt(0) != '/'
        || path.indexOf(',') >= 0
        || path.indexOf('=') >= 0
        || path.indexOf('%') >= 0
        || path.indexOf('@') >= 0
        || path.indexOf('?') >= 0
        || path.indexOf('#') >= 0) {
      return null;
    }
    return new DbServerTarget(path, null);
  }

  DbServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  /** Returns the value for {@code server.address}. */
  public String getAddress() {
    return address;
  }

  /**
   * Returns the value for {@code server.port}, or {@code null} when the target listens on its
   * default port or already carries its ports inside {@link #getAddress()}.
   */
  @Nullable
  public Integer getPort() {
    return port;
  }
}
