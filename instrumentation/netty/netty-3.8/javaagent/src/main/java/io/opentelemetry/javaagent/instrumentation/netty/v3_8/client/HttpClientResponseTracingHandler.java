/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private final VirtualField<Channel, ChannelTraceContext> virtualField;

  public HttpClientResponseTracingHandler(VirtualField<Channel, ChannelTraceContext> virtualField) {
    this.virtualField = virtualField;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
    ChannelTraceContext channelTraceContext =
        virtualField.computeIfNull(ctx.getChannel(), ChannelTraceContext.FACTORY);

    Context context = channelTraceContext.getContext();
    if (context == null) {
      ctx.sendUpstream(msg);
      return;
    }

    if (msg.getMessage() instanceof HttpResponse) {
      tracer().end(context, (HttpResponse) msg.getMessage());
    }

    // We want the callback in the scope of the parent, not the client span
    Context parentContext = channelTraceContext.getClientParentContext();
    if (parentContext != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        ctx.sendUpstream(msg);
      }
    } else {
      ctx.sendUpstream(msg);
    }
  }
}
