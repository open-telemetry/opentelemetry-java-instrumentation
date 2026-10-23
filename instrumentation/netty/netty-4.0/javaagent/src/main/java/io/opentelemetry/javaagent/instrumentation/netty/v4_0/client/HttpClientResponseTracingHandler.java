/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.nio.channels.ClosedChannelException;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  private Context upgradeParentContext;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    HttpClientRequestQueue queue = HttpClientRequestQueue.get(ctx.channel());
    HttpClientRequestQueue.Request request = queue.peek();
    if (request == null) {
      if (msg instanceof LastHttpContent && upgradeParentContext != null) {
        Context parentContext = upgradeParentContext;
        upgradeParentContext = null;
        try (Scope ignored = parentContext.makeCurrent()) {
          super.channelRead(ctx, msg);
        }
        return;
      }
      super.channelRead(ctx, msg);
      return;
    }

    if (msg instanceof HttpResponse) {
      queue.response((HttpResponse) msg);
      if (!(msg instanceof FullHttpResponse) && ((HttpResponse) msg).getStatus().code() == 101) {
        upgradeParentContext = request.parentContext;
      }
    }
    if (msg instanceof FullHttpResponse
        || msg instanceof LastHttpContent
        || (msg instanceof HttpResponse && ((HttpResponse) msg).getStatus().code() == 101)) {
      queue.endResponse();
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope ignored = request.parentContext.makeCurrent()) {
      super.channelRead(ctx, msg);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    upgradeParentContext = null;
    HttpClientRequestQueue.get(ctx.channel()).fail(cause);
    super.exceptionCaught(ctx, cause);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    upgradeParentContext = null;
    HttpClientRequestQueue.get(ctx.channel()).fail(new ClosedChannelException());
    super.channelInactive(ctx);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    upgradeParentContext = null;
    super.handlerRemoved(ctx);
  }
}
