/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * Holds the address of the socket a connection is actually using. A {@code VirtualField} is
 * identified by its owner type and its field type, so the peer needs a type of its own to sit
 * alongside the {@code InetSocketAddress} fields that carry the configured server address.
 *
 * <p>Commands share the holder that was current when they were dispatched. Lettuce buffers
 * still-queued commands when a channel goes down and rewrites them on the next channel, which may
 * reach a different socket, so a channel that goes down invalidates its holder and every command
 * that shares it stops reporting a peer.
 */
public class LettucePeerAddress {
  @Nullable private volatile InetSocketAddress address;

  public LettucePeerAddress(InetSocketAddress address) {
    this.address = address;
  }

  @Nullable
  InetSocketAddress getAddress() {
    return address;
  }

  void invalidate() {
    address = null;
  }
}
