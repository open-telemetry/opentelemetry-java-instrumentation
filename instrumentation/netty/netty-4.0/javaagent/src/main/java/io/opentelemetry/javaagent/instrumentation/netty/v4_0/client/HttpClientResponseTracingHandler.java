/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyClientSingletons.instrumenter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT);
    Context context = contextAttr.get();
    if (context == null) {
      ctx.fireChannelRead(msg);
      return;
    }

    Attribute<Context> parentContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Attribute<HttpRequestAndChannel> requestAttr = ctx.channel().attr(AttributeKeys.CLIENT_REQUEST);

    Context parentContext = parentContextAttr.get();
    HttpRequestAndChannel request = requestAttr.get();

    if (msg instanceof FullHttpResponse) {
      parentContextAttr.remove();
      contextAttr.remove();
      requestAttr.remove();
    } else if (msg instanceof HttpResponse) {
      // Headers before body have been received, store them to use when finishing the span.
      ctx.channel().attr(AttributeKeys.CLIENT_RESPONSE).set((HttpResponse) msg);
    } else if (msg instanceof LastHttpContent) {
      // Not a FullHttpResponse so this is content that has been received after headers. Finish the
      // span using what we stored in attrs.
      parentContextAttr.remove();
      contextAttr.remove();
      requestAttr.remove();
    }

    // We want the callback in the scope of the parent, not the client span
    if (parentContext != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        ctx.fireChannelRead(msg);
      }
    } else {
      ctx.fireChannelRead(msg);
    }

    if (msg instanceof FullHttpResponse) {
      instrumenter().end(context, request, (HttpResponse) msg, null);
    } else if (msg instanceof LastHttpContent) {
      HttpResponse response = ctx.channel().attr(AttributeKeys.CLIENT_RESPONSE).getAndRemove();
      instrumenter().end(context, request, response, null);
    }
  }
}
