/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.NettyHttpServerInstrumenter.tracer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Channel channel = ctx.channel();

    if (!(msg instanceof HttpRequest)) {
      Context serverContext = tracer().getServerContext(channel);
      if (serverContext == null) {
        ctx.fireChannelRead(msg);
      } else {
        try (Scope ignored = serverContext.makeCurrent()) {
          ctx.fireChannelRead(msg);
        }
      }
      return;
    }

    Context context = tracer().startOperation((HttpRequest) msg, channel, channel, "netty.request");
    try (Scope ignored = context.makeCurrent()) {
      ctx.fireChannelRead(msg);
      // the span is ended normally in HttpServerResponseTracingHandler
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
