/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import static java.util.Objects.requireNonNull;

import java.net.SocketAddress;

/** A Unix domain socket address for use with {@link NetworkAttributesGetter}. */
public final class UnixSocketAddress extends SocketAddress {

  private static final long serialVersionUID = 1L;

  private final String path;

  /** Creates an address with the given socket path. An empty path denotes an unnamed socket. */
  public UnixSocketAddress(String path) {
    this.path = requireNonNull(path, "path");
  }

  /** Returns the socket path, or an empty string for an unnamed socket. */
  public String getPath() {
    return path;
  }
}
