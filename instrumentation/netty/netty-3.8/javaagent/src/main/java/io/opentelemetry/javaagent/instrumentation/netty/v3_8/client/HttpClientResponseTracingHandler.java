/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.tracer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequestContexts;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private static final VirtualField<Channel, NettyRequestContexts> requestContextsField =
      VirtualField.find(Channel.class, NettyRequestContexts.class);

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
    NettyRequestContexts requestContexts = requestContextsField.get(ctx.getChannel());

    if (requestContexts == null || requestContexts.parentContext() == null) {
      ctx.sendUpstream(msg);
      return;
    }

    if (msg.getMessage() instanceof HttpResponse) {
      tracer().end(requestContexts.context(), (HttpResponse) msg.getMessage());
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope ignored = requestContexts.parentContext().makeCurrent()) {
      ctx.sendUpstream(msg);
    }
  }
}
