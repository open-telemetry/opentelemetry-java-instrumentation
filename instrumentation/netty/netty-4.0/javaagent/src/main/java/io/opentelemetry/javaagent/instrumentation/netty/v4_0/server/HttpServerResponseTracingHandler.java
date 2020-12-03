/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.NettyHttpServerTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context context = tracer().getServerContext(ctx.channel());
    if (context == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
    tracer().end(context, (HttpResponse) msg);
  }
}
