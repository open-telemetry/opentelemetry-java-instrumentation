/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static java.util.Objects.requireNonNull;

import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class UnixSocketAddress extends SocketAddress {

  private static final long serialVersionUID = 1L;

  private final String path;
  @Nullable private final transient SocketAddress originalAddress;

  /** Creates an address with the given socket path. An empty path denotes an unnamed socket. */
  public UnixSocketAddress(String path) {
    this(path, null);
  }

  /** Adapts a library-specific address with the given socket path. */
  public UnixSocketAddress(String path, @Nullable SocketAddress originalAddress) {
    this.path = requireNonNull(path, "path");
    this.originalAddress = originalAddress;
  }

  /** Returns the socket path, or an empty string for an unnamed socket. */
  public String getPath() {
    return path;
  }

  /** Returns the original address when adapting another address, or this address otherwise. */
  public SocketAddress getOriginalAddress() {
    return originalAddress == null ? this : originalAddress;
  }
}
