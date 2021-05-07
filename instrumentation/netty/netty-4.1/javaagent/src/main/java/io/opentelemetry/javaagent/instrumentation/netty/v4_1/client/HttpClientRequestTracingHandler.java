/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    Context parentContext = ctx.channel().attr(AttributeKeys.WRITE_CONTEXT).getAndRemove();
    if (parentContext == null) {
      parentContext = Context.current();
    }

    if (!tracer().shouldStartSpan(parentContext, (HttpRequest) msg)) {
      ctx.write(msg, prm);
      return;
    }

    Context context = tracer().startSpan(parentContext, ctx, (HttpRequest) msg);

    Attribute<Context> clientContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT);
    Attribute<Context> parentContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    clientContextAttr.set(context);
    parentContextAttr.set(parentContext);

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
      // span is ended normally in HttpClientResponseTracingHandler
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      clientContextAttr.remove();
      parentContextAttr.remove();
      throw throwable;
    }
  }
}
