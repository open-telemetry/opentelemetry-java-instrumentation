/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyConnectionContext;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequestContexts;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  private static final VirtualField<Channel, NettyConnectionContext> connectionContextField =
      VirtualField.find(Channel.class, NettyConnectionContext.class);
  private static final VirtualField<Channel, NettyRequestContexts> requestContextsField =
      VirtualField.find(Channel.class, NettyRequestContexts.class);

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent event) {
    Object message = event.getMessage();
    if (!(message instanceof HttpRequest)) {
      ctx.sendDownstream(event);
      return;
    }

    Context parentContext = null;
    NettyConnectionContext connectionContext = connectionContextField.get(ctx.getChannel());
    if (connectionContext != null) {
      parentContext = connectionContext.remove();
    }
    if (parentContext == null) {
      parentContext = Context.current();
    }

    if (!tracer().shouldStartSpan(parentContext)) {
      ctx.sendDownstream(event);
      return;
    }

    Context context = tracer().startSpan(parentContext, ctx, (HttpRequest) message);
    requestContextsField.set(ctx.getChannel(), NettyRequestContexts.create(parentContext, context));

    try (Scope ignored = context.makeCurrent()) {
      ctx.sendDownstream(event);
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
