/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequestContexts;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends SimpleChannelUpstreamHandler {

  private static final VirtualField<Channel, NettyRequestContexts> requestContextsField =
      VirtualField.find(Channel.class, NettyRequestContexts.class);

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) {
    Object message = event.getMessage();
    if (!(message instanceof HttpRequest)) {
      NettyRequestContexts requestContexts = requestContextsField.get(ctx.getChannel());
      if (requestContexts == null) {
        ctx.sendUpstream(event);
      } else {
        try (Scope ignored = requestContexts.context().makeCurrent()) {
          ctx.sendUpstream(event);
        }
      }
      return;
    }

    HttpRequest request = (HttpRequest) message;

    Context context =
        tracer().startSpan(request, ctx.getChannel(), null, "HTTP " + request.getMethod());
    requestContextsField.set(ctx.getChannel(), NettyRequestContexts.create(null, context));

    try (Scope ignored = context.makeCurrent()) {
      ctx.sendUpstream(event);
      // the span is ended normally in HttpServerResponseTracingHandler
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
