/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v3_8.client;

import static io.opentelemetry.instrumentation.auto.netty.v3_8.client.NettyHttpClientTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.netty.v3_8.ChannelTraceContext;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;
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

    Span parent = channelTraceContext.getClientParentSpan();
    if (parent == null) {
      parent = DefaultSpan.getInvalid();
      channelTraceContext.setClientParentSpan(DefaultSpan.getInvalid());
    }
    Span span = channelTraceContext.getClientSpan();

    boolean finishSpan = msg.getMessage() instanceof HttpResponse;

    if (span != null && finishSpan) {
      TRACER.end(span, (HttpResponse) msg.getMessage());
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope scope = TracingContextUtils.currentContextWith(parent)) {
      ctx.sendUpstream(msg);
    }
  }
}
