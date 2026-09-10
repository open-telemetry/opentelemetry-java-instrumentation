/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

class LettuceCommandPeer {
  private static final String DOMAIN_SOCKET_ADDRESS_CLASS =
      "io.netty.channel.unix.DomainSocketAddress";

  private static final ClassValue<Method> domainSocketAddressPathMethod =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("path");
          } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
          }
        }
      };

  private final AtomicBoolean spanStarted = new AtomicBoolean();
  @Nullable private volatile SocketAddress address;

  void record(SocketAddress address) {
    this.address = address;
  }

  @Nullable
  SocketAddress getAddress() {
    return address;
  }

  boolean markSpanStarted() {
    return spanStarted.compareAndSet(false, true);
  }

  @Nullable
  static String getNetworkPeerAddress(@Nullable SocketAddress peerAddress) {
    if (peerAddress instanceof InetSocketAddress) {
      InetSocketAddress inetPeerAddress = (InetSocketAddress) peerAddress;
      return inetPeerAddress.isUnresolved() ? null : inetPeerAddress.getAddress().getHostAddress();
    }
    if (peerAddress != null
        && peerAddress.getClass().getName().equals(DOMAIN_SOCKET_ADDRESS_CLASS)) {
      Method pathMethod = domainSocketAddressPathMethod.get(peerAddress.getClass());
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
  static Integer getNetworkPeerPort(@Nullable SocketAddress peerAddress) {
    if (!(peerAddress instanceof InetSocketAddress)) {
      return null;
    }
    InetSocketAddress inetPeerAddress = (InetSocketAddress) peerAddress;
    return inetPeerAddress.isUnresolved() ? null : inetPeerAddress.getPort();
  }
}
