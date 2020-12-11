/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context parentContext = ctx.channel().attr(AttributeKeys.CONNECT_CONTEXT).getAndRemove();
    if (parentContext == null) {
      parentContext = Context.current();
    }
    Context context = tracer().startOperation(parentContext, ctx, msg);
    ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT).set(context);
    ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(parentContext);
    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
