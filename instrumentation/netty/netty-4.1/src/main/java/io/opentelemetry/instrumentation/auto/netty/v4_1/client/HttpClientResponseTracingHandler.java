/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v4_1.client;

import static io.opentelemetry.instrumentation.auto.netty.v4_1.client.NettyHttpClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.netty.v4_1.AttributeKeys;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    Attribute<Span> parentAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(DefaultSpan.getInvalid());
    Span parent = parentAttr.get();
    Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();

    boolean finishSpan = msg instanceof HttpResponse;

    if (span != null && finishSpan) {
      TRACER.end(span, (HttpResponse) msg);
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope scope = currentContextWith(parent)) {
      ctx.fireChannelRead(msg);
    }
  }
}
