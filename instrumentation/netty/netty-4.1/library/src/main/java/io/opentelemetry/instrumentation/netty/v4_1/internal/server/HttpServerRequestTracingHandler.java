/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  static final AttributeKey<HttpRequestAndChannel> HTTP_REQUEST =
      AttributeKey.valueOf(HttpServerRequestTracingHandler.class, "http-server-request");

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;

  public HttpServerRequestTracingHandler(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Channel channel = ctx.channel();
    Attribute<Context> contextAttr = channel.attr(AttributeKeys.SERVER_CONTEXT);
    Attribute<HttpRequestAndChannel> requestAttr = channel.attr(HTTP_REQUEST);

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

    if (!instrumenter.shouldStart(parentContext, request)) {
      ctx.fireChannelRead(msg);
      return;
    }

    Context context = instrumenter.start(parentContext, request);
    contextAttr.set(context);
    requestAttr.set(request);

    try (Scope ignored = context.makeCurrent()) {
      ctx.fireChannelRead(msg);
      // the span is ended normally in HttpServerResponseTracingHandler
    } catch (Throwable throwable) {
      // make sure to remove the server context on end() call
      instrumenter.end(contextAttr.getAndSet(null), requestAttr.getAndSet(null), null, throwable);
      throw throwable;
    }
  }
}
