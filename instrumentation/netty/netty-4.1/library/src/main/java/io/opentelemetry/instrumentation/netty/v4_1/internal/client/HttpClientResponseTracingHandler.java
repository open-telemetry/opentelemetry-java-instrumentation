/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_REQUEST;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  private static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(HttpClientResponseTracingHandler.class, "http-client-response");

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;

  public HttpClientResponseTracingHandler(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT);
    Context context = contextAttr.get();
    if (context == null) {
      super.channelRead(ctx, msg);
      return;
    }

    Attribute<Context> parentContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Attribute<HttpRequestAndChannel> requestAttr = ctx.channel().attr(HTTP_REQUEST);

    Context parentContext = parentContextAttr.get();
    HttpRequestAndChannel request = requestAttr.get();

    if (msg instanceof FullHttpResponse) {
      parentContextAttr.set(null);
      contextAttr.set(null);
      requestAttr.set(null);
    } else if (msg instanceof HttpResponse) {
      // Headers before body have been received, store them to use when finishing the span.
      ctx.channel().attr(HTTP_RESPONSE).set((HttpResponse) msg);
    } else if (msg instanceof LastHttpContent) {
      // Not a FullHttpResponse so this is content that has been received after headers. Finish the
      // span using what we stored in attrs.
      parentContextAttr.set(null);
      contextAttr.set(null);
      requestAttr.set(null);
    }

    // We want the callback in the scope of the parent, not the client span
    if (parentContext != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        super.channelRead(ctx, msg);
      }
    } else {
      super.channelRead(ctx, msg);
    }

    if (msg instanceof FullHttpResponse) {
      instrumenter.end(context, request, (HttpResponse) msg, null);
    } else if (msg instanceof LastHttpContent) {
      instrumenter.end(context, request, ctx.channel().attr(HTTP_RESPONSE).getAndSet(null), null);
    }
  }
}
