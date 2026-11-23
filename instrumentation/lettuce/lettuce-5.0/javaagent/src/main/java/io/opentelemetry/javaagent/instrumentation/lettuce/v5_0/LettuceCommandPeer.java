/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.DecoratedCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

public final class LettuceCommandPeer {
  private static final VirtualField<RedisCommand<?, ?, ?>, LettuceCommandPeer> COMMAND_PEER =
      VirtualField.find(RedisCommand.class, LettuceCommandPeer.class);

  private final AtomicBoolean spanStarted = new AtomicBoolean();
  @Nullable private volatile SocketAddress address;

  public static void initialize(RedisCommand<?, ?, ?> command) {
    if (COMMAND_PEER.get(command) == null) {
      COMMAND_PEER.set(command, new LettuceCommandPeer());
    }
  }

  public static void initializeForSubscription(RedisCommand<?, ?, ?> command) {
    COMMAND_PEER.set(command, new LettuceCommandPeer());
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

  private LettuceCommandPeer() {}
}
