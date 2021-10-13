/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.NettyServerSingletons.instrumenter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.instrumentation.netty.common.server.HttpRequestAndChannel;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Channel channel = ctx.channel();
    Attribute<Context> contextAttr = channel.attr(AttributeKeys.SERVER_CONTEXT);
    Attribute<HttpRequestAndChannel> requestAttr = channel.attr(NettyServerSingletons.HTTP_REQUEST);

    if (!(msg instanceof HttpRequest)) {
      Context serverContext = contextAttr.get();
      if (serverContext == null) {
        ctx.fireChannelRead(msg);
      } else {
        try (Scope ignored = serverContext.makeCurrent()) {
          ctx.fireChannelRead(msg);
        }
      }
      return;
    }

    Context parentContext = contextAttr.get();
    if (parentContext == null) {
      parentContext = Context.current();
    }
    HttpRequestAndChannel request = HttpRequestAndChannel.create((HttpRequest) msg, channel);

    if (!instrumenter().shouldStart(parentContext, request)) {
      ctx.fireChannelRead(msg);
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    contextAttr.set(context);
    requestAttr.set(request);

    try (Scope ignored = context.makeCurrent()) {
      ctx.fireChannelRead(msg);
      // the span is ended normally in HttpServerResponseTracingHandler
    } catch (Throwable throwable) {
      // make sure to remove the server context on end() call
      instrumenter().end(contextAttr.getAndRemove(), requestAttr.getAndRemove(), null, throwable);
      throw throwable;
    }
  }
}
