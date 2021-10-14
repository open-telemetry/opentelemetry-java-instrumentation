/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.NettyServerSingletons.instrumenter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import org.checkerframework.checker.nullness.qual.Nullable;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
    if (context == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
      end(ctx.channel(), (HttpResponse) msg, null);
    } catch (Throwable throwable) {
      end(ctx.channel(), (HttpResponse) msg, throwable);
      throw throwable;
    }
  }

  // make sure to remove the server context on end() call
  private static void end(Channel channel, HttpResponse response, @Nullable Throwable error) {
    Context context = channel.attr(AttributeKeys.SERVER_CONTEXT).getAndRemove();
    HttpRequestAndChannel request = channel.attr(AttributeKeys.SERVER_REQUEST).getAndRemove();
    instrumenter().end(context, request, response, error);
  }
}
