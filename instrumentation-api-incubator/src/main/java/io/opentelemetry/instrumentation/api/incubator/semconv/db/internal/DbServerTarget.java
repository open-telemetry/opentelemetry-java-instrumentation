/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/**
 * A database endpoint set rendered as an address and a port.
 *
 * <p>A target usually names the logical server a database client was configured to talk to,
 * rendered as {@code server.address} and {@code server.port}. Such a target stays the same across
 * routing, node selection, and retries, so it is derived from client configuration rather than from
 * the endpoint that served an individual operation.
 *
 * <p>An instrumentation that knows which endpoint served an individual operation renders that one
 * endpoint the same way and reports it as {@code network.peer.address} and {@code
 * network.peer.port}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@AutoValue
public abstract class DbServerTarget {

  /**
   * Creates a target from already parsed and sanitized attribute values without further
   * normalization. Use {@link #builder()} when collecting raw endpoints.
   */
  public static DbServerTarget create(String address, @Nullable Integer port) {
    return new AutoValue_DbServerTarget(address, port);
  }

  /**
   * Returns a builder for a target whose endpoints listen on {@code defaultPort} unless they are
   * configured otherwise.
   */
  public static DbServerTargetBuilder builder(int defaultPort) {
    return new DbServerTargetBuilder(defaultPort);
  }

  /**
   * Returns a builder for a target whose default port is unknown. Endpoints without configured
   * ports are kept without ports.
   */
  public static DbServerTargetBuilder builder() {
    return new DbServerTargetBuilder(null);
  }

  /**
   * Returns a target for an already-extracted Unix socket path, or {@code null} when the path is
   * invalid. Accepted paths are preserved verbatim and are not parsed as URIs or connection
   * strings.
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
    return create(path, null);
  }

  DbServerTarget() {}

  /** Returns the value for {@code server.address}, or for {@code network.peer.address}. */
  public abstract String getAddress();

  /**
   * Returns the value for {@code server.port}, or for {@code network.peer.port}, or {@code null}
   * when no separate port is reported. Targets built with {@link DbServerTargetBuilder} omit
   * default ports and ports already carried inside {@link #getAddress()}.
   */
  @Nullable
  public abstract Integer getPort();
}
