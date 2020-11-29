/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Attribute<Context> parentAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Context parentContext = parentAttr.get();
    Span span = ctx.channel().attr(AttributeKeys.CLIENT_SPAN).get();

    boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      tracer().end(span, (HttpResponse) msg);
    }

    // We want the callback in the scope of the parent, not the client span
    if (parentContext != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        ctx.fireChannelRead(msg);
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }
}
