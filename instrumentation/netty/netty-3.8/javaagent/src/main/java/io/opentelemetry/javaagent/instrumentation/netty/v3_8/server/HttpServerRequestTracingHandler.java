/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpServerRequestTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) {
    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.FACTORY);

    Object message = event.getMessage();
    if (!(message instanceof HttpRequest)) {
      Context serverContext = tracer().getServerContext(channelTraceContext);
      if (serverContext == null) {
        ctx.sendUpstream(event);
      } else {
        try (Scope ignored = serverContext.makeCurrent()) {
          ctx.sendUpstream(event);
        }
      }
      return;
    }

    HttpRequest request = (HttpRequest) message;

    Context context =
        tracer()
            .startSpan(
                request, ctx.getChannel(), channelTraceContext, "HTTP " + request.getMethod());
    try (Scope ignored = context.makeCurrent()) {
      ctx.sendUpstream(event);
      // the span is ended normally in HttpServerResponseTracingHandler
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
