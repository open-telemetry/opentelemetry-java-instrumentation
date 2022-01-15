/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.instrumenter;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private static final VirtualField<Channel, NettyClientRequestAndContexts> requestContextsField =
      VirtualField.find(Channel.class, NettyClientRequestAndContexts.class);

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
    NettyClientRequestAndContexts requestAndContexts = requestContextsField.get(ctx.getChannel());

    if (requestAndContexts == null) {
      ctx.sendUpstream(msg);
      return;
    }

    if (msg.getMessage() instanceof HttpResponse) {
      instrumenter()
          .end(
              requestAndContexts.context(),
              requestAndContexts.request(),
              (HttpResponse) msg.getMessage(),
              NettyErrorHolder.getOrDefault(requestAndContexts.context(), null));
      requestContextsField.set(ctx.getChannel(), null);
    }

    // We want the callback in the scope of the parent, not the client span
    try (Scope ignored = requestAndContexts.parentContext().makeCurrent()) {
      ctx.sendUpstream(msg);
    }
  }
}
