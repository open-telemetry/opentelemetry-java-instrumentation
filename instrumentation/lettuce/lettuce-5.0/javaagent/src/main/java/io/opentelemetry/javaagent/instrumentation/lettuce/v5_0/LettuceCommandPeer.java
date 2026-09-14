/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.DecoratedCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

public final class LettuceCommandPeer {
  private static final String DOMAIN_SOCKET_ADDRESS_CLASS =
      "io.netty.channel.unix.DomainSocketAddress";

  private static final VirtualField<RedisCommand<?, ?, ?>, LettuceCommandPeer> COMMAND_PEER =
      VirtualField.find(RedisCommand.class, LettuceCommandPeer.class);

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

  public static void initialize(RedisCommand<?, ?, ?> command) {
    if (COMMAND_PEER.get(command) == null) {
      COMMAND_PEER.set(command, new LettuceCommandPeer());
    }
  }

  public static void initializeForSubscription(RedisCommand<?, ?, ?> command) {
    if (find(command) == null) {
      COMMAND_PEER.set(command, new LettuceCommandPeer());
    }
  }

  public static void record(RedisCommand<?, ?, ?> command, SocketAddress peerAddress) {
    LettuceCommandPeer peer = find(command);
    if (peer != null) {
      peer.address = peerAddress;
    }
  }

  public static boolean markSpanStarted(AsyncCommand<?, ?, ?> command) {
    LettuceCommandPeer peer = find(command);
    return peer != null && peer.spanStarted.compareAndSet(false, true);
  }

  @Nullable
  static SocketAddress address(RedisCommand<?, ?, ?> command) {
    // A command that does not expect a response has its span ended synchronously in
    // DefaultEndpoint.write, while the channel write that records the peer runs later on the netty
    // event loop, so the peer is not known yet.
    if (!LettuceInstrumentationUtil.expectsResponse(command)) {
      return null;
    }
    LettuceCommandPeer peer = find(command);
    return peer == null ? null : peer.address;
  }

  @Nullable
  static SocketAddress batchAddress(List<RedisCommand<?, ?, ?>> commands) {
    SocketAddress batchPeerAddress = null;
    for (RedisCommand<?, ?, ?> command : commands) {
      LettuceCommandPeer peer = find(command);
      if (peer == null || peer.address == null) {
        return null;
      }
      if (batchPeerAddress == null) {
        batchPeerAddress = peer.address;
      } else if (!batchPeerAddress.equals(peer.address)) {
        return null;
      }
    }
    return batchPeerAddress;
  }

  @Nullable
  private static LettuceCommandPeer find(RedisCommand<?, ?, ?> command) {
    RedisCommand<?, ?, ?> current = command;
    while (current != null) {
      LettuceCommandPeer peer = COMMAND_PEER.get(current);
      if (peer != null) {
        return peer;
      }
      current =
          current instanceof DecoratedCommand
              ? ((DecoratedCommand<?, ?, ?>) current).getDelegate()
              : null;
    }
    return null;
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

  private LettuceCommandPeer() {}
}
