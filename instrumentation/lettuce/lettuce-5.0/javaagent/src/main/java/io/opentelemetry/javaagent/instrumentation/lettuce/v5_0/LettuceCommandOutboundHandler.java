/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.net.SocketAddress;
import java.util.Collection;

public class LettuceCommandOutboundHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
    try {
      SocketAddress remoteAddress = context.channel().remoteAddress();
      if (remoteAddress != null) {
        recordCommands(message, remoteAddress);
      }
    } catch (Throwable ignored) {
      // Do not let telemetry collection disrupt Redis I/O.
    }
    context.write(message, promise);
  }

  static void recordCommands(Object message, SocketAddress remoteAddress) {
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

  private static void recordCommand(RedisCommand<?, ?, ?> command, SocketAddress remoteAddress) {
    LettuceSingletons.recordCommandPeer(command, remoteAddress);
  }
}
