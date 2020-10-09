/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyHttpClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.Context;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import io.opentelemetry.trace.Span;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Attribute<Context> parentAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY);
    Context parentContext = parentAttr.get();
    Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      try (Scope scope = currentContextWith(span)) {
        TRACER.end(span, (HttpResponse) msg);
      }
    }

    // We want the callback in the scope of the parent, not the client span
    if (parentContext != null) {
      try (Scope ignored = withScopedContext(parentContext)) {
        ctx.fireChannelRead(msg);
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }
}
