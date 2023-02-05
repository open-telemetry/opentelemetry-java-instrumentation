/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_REQUEST;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
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
    if (!(msg instanceof HttpResponse) && !(msg instanceof HttpContent)) {
      super.channelRead(ctx, msg);
      return;
    }

    Attribute<Context> parentContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Context parentContext = parentContextAttr.get();

    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT);
    Context context = contextAttr.get();

    if (context != null) {
      if (msg instanceof FullHttpResponse) {
        HttpRequestAndChannel request = ctx.channel().attr(HTTP_REQUEST).getAndSet(null);
        instrumenter.end(context, request, (HttpResponse) msg, null);
        contextAttr.set(null);
        parentContextAttr.set(null);
      } else if (msg instanceof HttpResponse) {
        // Headers before body have been received, store them to use when finishing the span.
        ctx.channel().attr(HTTP_RESPONSE).set((HttpResponse) msg);
      } else if (msg instanceof LastHttpContent) {
        // Not a FullHttpResponse so this is content that has been received after headers.
        // Finish the span using what we stored in attrs.
        HttpRequestAndChannel request = ctx.channel().attr(HTTP_REQUEST).getAndSet(null);
        HttpResponse response = ctx.channel().attr(HTTP_RESPONSE).getAndSet(null);
        instrumenter.end(context, request, response, null);
        contextAttr.set(null);
        parentContextAttr.set(null);
      }
    }

    // We want the callback in the scope of the parent, not the client span
    if (parentContext != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        super.channelRead(ctx, msg);
      }
    } else {
      super.channelRead(ctx, msg);
    }
  }
}
