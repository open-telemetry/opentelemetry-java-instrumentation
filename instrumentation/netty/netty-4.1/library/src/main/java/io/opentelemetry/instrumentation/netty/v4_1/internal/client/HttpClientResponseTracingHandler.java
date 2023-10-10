/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_CLIENT_REQUEST;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.TypeUtils;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  static final AttributeKey<HttpResponse> HTTP_CLIENT_RESPONSE =
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
    Context parentContext = parentContextAttr.get();

    if (TypeUtils.isFullHttpResponse(msg)) {
      HttpRequestAndChannel request = ctx.channel().attr(HTTP_CLIENT_REQUEST).getAndSet(null);
      instrumenter.end(context, request, (HttpResponse) msg, null);
      contextAttr.set(null);
      parentContextAttr.set(null);
    } else if (TypeUtils.isHttpResponse(msg)) {
      // Headers before body have been received, store them to use when finishing the span.
      ctx.channel().attr(HTTP_CLIENT_RESPONSE).set((HttpResponse) msg);
    } else if (TypeUtils.isLastHttpContent(msg)) {
      // Not a FullHttpResponse so this is content that has been received after headers.
      // Finish the span using what we stored in attrs.
      HttpRequestAndChannel request = ctx.channel().attr(HTTP_CLIENT_REQUEST).getAndSet(null);
      HttpResponse response = ctx.channel().attr(HTTP_CLIENT_RESPONSE).getAndSet(null);
      instrumenter.end(context, request, response, null);
      contextAttr.set(null);
      parentContextAttr.set(null);
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
