/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.CommandWrapper;
import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class LettuceCommandOutboundHandler extends ChannelOutboundHandlerAdapter {
  public static final String NAME = LettuceCommandOutboundHandler.class.getName();

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
    if (message instanceof RedisCommand) {
      SocketAddress remoteAddress = context.channel().remoteAddress();
      if (remoteAddress instanceof InetSocketAddress) {
        RedisCommand<?, ?, ?> command = (RedisCommand<?, ?, ?>) message;
        while (command != null) {
          LettuceSingletons.recordCommandPeer(command, (InetSocketAddress) remoteAddress);
          command =
              command instanceof CommandWrapper
                  ? ((CommandWrapper<?, ?, ?>) command).getDelegate()
                  : null;
        }
      }
    }
    context.write(message, promise);
  }
}
