/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v4_0.server;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.netty.v4_0.server.NettyHttpServerTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.Context;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Channel channel = ctx.channel();

    if (!(msg instanceof HttpRequest)) {
      Context serverContext = TRACER.getServerContext(channel);
      if (serverContext == null) {
        ctx.fireChannelRead(msg);
      } else {
        try (Scope ignored = withScopedContext(serverContext)) {
          ctx.fireChannelRead(msg);
        }
      }
      return;
    }

    Span span = TRACER.startSpan((HttpRequest) msg, channel, "netty.request");
    try (Scope ignored = TRACER.startScope(span, channel)) {
      ctx.fireChannelRead(msg);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    }
  }
}
