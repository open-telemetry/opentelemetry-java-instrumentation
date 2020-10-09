/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v4_1.server;

import static io.opentelemetry.instrumentation.auto.netty.v4_1.server.NettyHttpServerTracer.TRACER;

import io.grpc.Context;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context context = TRACER.getServerContext(ctx.channel());
    if (context == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    Span span = TracingContextUtils.getSpan(context);
    try (Scope ignored = ContextUtils.withScopedContext(context)) {
      ctx.write(msg, prm);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    }
    TRACER.end(span, (HttpResponse) msg);
  }
}
