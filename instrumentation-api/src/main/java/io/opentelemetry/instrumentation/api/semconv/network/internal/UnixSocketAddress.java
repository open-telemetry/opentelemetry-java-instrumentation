/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static java.util.Objects.requireNonNull;

import java.net.SocketAddress;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
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
