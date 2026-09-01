/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;

public class LettuceCommandOutboundHandler extends ChannelOutboundHandlerAdapter {
  public static final String NAME = LettuceCommandOutboundHandler.class.getName();

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
    try {
      SocketAddress remoteAddress = context.channel().remoteAddress();
      if (remoteAddress instanceof InetSocketAddress) {
        InetSocketAddress peerAddress = (InetSocketAddress) remoteAddress;
        recordCommands(message, peerAddress);
      }
    } catch (Throwable ignored) {
      // Do not let telemetry collection disrupt Redis I/O.
    }
    context.write(message, promise);
  }

  static void recordCommands(Object message, InetSocketAddress remoteAddress) {
    if (message instanceof RedisCommand) {
      recordCommand((RedisCommand<?, ?, ?>) message, remoteAddress);
    } else if (message instanceof Collection) {
      for (Object item : (Collection<?>) message) {
        if (item instanceof RedisCommand) {
          recordCommand((RedisCommand<?, ?, ?>) item, remoteAddress);
        }
      }
    }
  }

  private static void recordCommand(
      RedisCommand<?, ?, ?> command, InetSocketAddress remoteAddress) {
    LettuceSingletons.recordCommandPeer(command, remoteAddress);
  }
}
