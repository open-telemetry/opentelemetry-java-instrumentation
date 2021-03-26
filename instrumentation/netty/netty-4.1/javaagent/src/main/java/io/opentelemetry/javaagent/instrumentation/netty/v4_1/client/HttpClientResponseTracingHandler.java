/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  private static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(HttpClientResponseTracingHandler.class, "HTTP_RESPONSE");

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Context context = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT).get();
    if (context == null) {
      ctx.fireChannelRead(msg);
      return;
    }

    if (msg instanceof FullHttpResponse) {
      tracer().end(context, (HttpResponse) msg);
    } else if (msg instanceof HttpResponse) {
      // Headers before body has been sent, store them to use when finishing the span.
      ctx.channel().attr(HTTP_RESPONSE).set((HttpResponse) msg);
    } else if (msg instanceof LastHttpContent) {
      // Not a FullHttpResponse so this is content that has been sent after headers. Finish the
      // span using what we stored in attrs.
      tracer().end(context, ctx.channel().attr(HTTP_RESPONSE).get());
    }

    // We want the callback in the scope of the parent, not the client span
    Attribute<Context> parentAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Context parentContext = parentAttr.get();
    if (parentContext != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        ctx.fireChannelRead(msg);
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }
}
