/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

class LettuceCommandPeer {
  private static final String DOMAIN_SOCKET_ADDRESS_CLASS =
      "io.netty.channel.unix.DomainSocketAddress";

  @Nullable private static volatile Method domainSocketAddressPathMethod;
  private static volatile boolean domainSocketAddressPathMethodInitialized;

  @Nullable private SocketAddress address;

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
      Method pathMethod = getDomainSocketAddressPathMethod(peerAddress.getClass());
      if (pathMethod == null) {
        return null;
      }
      try {
        return (String) pathMethod.invoke(peerAddress);
      } catch (ReflectiveOperationException
          | IllegalArgumentException
          | ClassCastException ignored) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static Method getDomainSocketAddressPathMethod(Class<?> addressClass) {
    if (!domainSocketAddressPathMethodInitialized) {
      synchronized (LettuceCommandPeer.class) {
        if (!domainSocketAddressPathMethodInitialized) {
          try {
            domainSocketAddressPathMethod = addressClass.getMethod("path");
          } catch (NoSuchMethodException | SecurityException ignored) {
            // Leave the method unset when this Netty version does not expose the path.
          }
          domainSocketAddressPathMethodInitialized = true;
        }
      }
    }
    return domainSocketAddressPathMethod;
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
