/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class LettucePeerAddress {
  private static final String DOMAIN_SOCKET_ADDRESS_CLASS =
      "io.netty.channel.unix.DomainSocketAddress";

  @Nullable private SocketAddress address;

  LettucePeerAddress() {}

  LettucePeerAddress(SocketAddress address) {
    this.address = address;
  }

  synchronized void record(SocketAddress address) {
    this.address = address;
  }

  @Nullable
  synchronized SocketAddress getAddress() {
    return address;
  }

  @Nullable
  static String getNetworkPeerAddress(@Nullable SocketAddress peerAddress) {
    if (peerAddress instanceof InetSocketAddress) {
      InetSocketAddress inetPeerAddress = (InetSocketAddress) peerAddress;
      return inetPeerAddress.isUnresolved() ? null : inetPeerAddress.getAddress().getHostAddress();
    }
    if (peerAddress != null
        && peerAddress.getClass().getName().equals(DOMAIN_SOCKET_ADDRESS_CLASS)) {
      try {
        return (String) peerAddress.getClass().getMethod("path").invoke(peerAddress);
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  static Integer getNetworkPeerPort(@Nullable SocketAddress peerAddress) {
    if (!(peerAddress instanceof InetSocketAddress)) {
      return null;
    }
    InetSocketAddress inetPeerAddress = (InetSocketAddress) peerAddress;
    return inetPeerAddress.isUnresolved() ? null : inetPeerAddress.getPort();
  }
}
