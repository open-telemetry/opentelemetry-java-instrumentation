/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyClientSingletons.instrumenter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;

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

    HttpRequestAndChannel request = HttpRequestAndChannel.create((HttpRequest) msg, ctx.channel());
    if (!instrumenter().shouldStart(parentContext, request)) {
      ctx.write(msg, prm);
      return;
    }

    Attribute<Context> parentContextAttr = ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT);
    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.CLIENT_CONTEXT);
    Attribute<HttpRequestAndChannel> requestAttr = ctx.channel().attr(AttributeKeys.CLIENT_REQUEST);

    Context context = instrumenter().start(parentContext, request);
    parentContextAttr.set(parentContext);
    contextAttr.set(context);
    requestAttr.set(request);

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
      // span is ended normally in HttpClientResponseTracingHandler
    } catch (Throwable throwable) {
      instrumenter().end(contextAttr.getAndRemove(), requestAttr.getAndRemove(), null, throwable);
      parentContextAttr.remove();
      throw throwable;
    }
  }
}
