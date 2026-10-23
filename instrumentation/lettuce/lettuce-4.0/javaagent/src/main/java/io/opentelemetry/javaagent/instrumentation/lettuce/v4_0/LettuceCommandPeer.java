/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class LettuceCommandPeer {
  private static final String DOMAIN_SOCKET_ADDRESS_CLASS =
      "io.netty.channel.unix.DomainSocketAddress";

  @Nullable
  private static final Method domainSocketAddressPathMethod = getDomainSocketAddressPathMethod();

  // Completion and outbound writes can race on different threads.
  @Nullable private SocketAddress address;
  private boolean finished;

  synchronized void record(SocketAddress address) {
    if (!finished) {
      this.address = address;
    }
  }

  synchronized void finish() {
    finished = true;
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
      if (domainSocketAddressPathMethod == null) {
        return null;
      }
      try {
        return (String) domainSocketAddressPathMethod.invoke(peerAddress);
      } catch (ReflectiveOperationException
          | IllegalArgumentException
          | ClassCastException ignored) {
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

  @Nullable
  private static Method getDomainSocketAddressPathMethod() {
    try {
      return Class.forName(
              DOMAIN_SOCKET_ADDRESS_CLASS, false, LettuceCommandPeer.class.getClassLoader())
          .getMethod("path");
    } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
      return null;
    }
  }
}
