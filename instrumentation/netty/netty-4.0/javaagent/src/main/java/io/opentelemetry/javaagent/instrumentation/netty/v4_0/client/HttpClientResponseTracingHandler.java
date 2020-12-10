/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    HttpClientOperation operation = ctx.channel().attr(AttributeKeys.CLIENT_OPERATION).get();
    if (operation == null) {
      ctx.fireChannelRead(msg);
      return;
    }
    if (msg instanceof HttpResponse) {
      tracer().end(operation, (HttpResponse) msg);
    }
    // We want the callback in the scope of the parent, not the client span
    try (Scope ignored = operation.makeParentCurrent()) {
      ctx.fireChannelRead(msg);
    }
  }
}
