/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends SimpleChannelUpstreamHandler {

  private static final VirtualField<Channel, NettyServerRequestAndContext> requestAndContextField =
      VirtualField.find(Channel.class, NettyServerRequestAndContext.class);

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
    Object message = event.getMessage();
    if (!(message instanceof HttpRequest)) {
      NettyServerRequestAndContext requestAndContext = requestAndContextField.get(ctx.getChannel());
      if (requestAndContext == null) {
        super.messageReceived(ctx, event);
      } else {
        try (Scope ignored = requestAndContext.context().makeCurrent()) {
          super.messageReceived(ctx, event);
        }
      }
      return;
    }

    Context parentContext = Context.current();
    HttpRequestAndChannel request =
        HttpRequestAndChannel.create((HttpRequest) message, ctx.getChannel());
    if (!instrumenter().shouldStart(parentContext, request)) {
      super.messageReceived(ctx, event);
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    requestAndContextField.set(
        ctx.getChannel(), NettyServerRequestAndContext.create(request, context));

    try (Scope ignored = context.makeCurrent()) {
      super.messageReceived(ctx, event);
    } catch (Throwable throwable) {
      instrumenter().end(context, request, null, throwable);
      throw throwable;
    }
    // span is ended normally in HttpServerResponseTracingHandler
  }
}
