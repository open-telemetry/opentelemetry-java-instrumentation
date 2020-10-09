/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.TRACER;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.trace.Span;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpClientResponseTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    Context parentContext = channelTraceContext.getClientParentContext();
    Span span = channelTraceContext.getClientSpan();

    boolean finishSpan = msg.getMessage() instanceof HttpResponse;

    if (span != null && finishSpan) {
      TRACER.end(span, (HttpResponse) msg.getMessage());
    }

    // We want the callback in the scope of the parent, not the client span
    if (parentContext != null) {
      try (Scope ignored = withScopedContext(parentContext)) {
        ctx.sendUpstream(msg);
      }
    } else {
      ctx.sendUpstream(msg);
    }
  }
}
