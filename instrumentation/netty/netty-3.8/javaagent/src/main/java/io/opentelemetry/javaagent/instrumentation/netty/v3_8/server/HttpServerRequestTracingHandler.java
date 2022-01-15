/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
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
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) {
    Object message = event.getMessage();
    if (!(message instanceof HttpRequest)) {
      NettyServerRequestAndContext requestAndContext = requestAndContextField.get(ctx.getChannel());
      if (requestAndContext == null) {
        ctx.sendUpstream(event);
      } else {
        try (Scope ignored = requestAndContext.context().makeCurrent()) {
          ctx.sendUpstream(event);
        }
      }
      return;
    }

    Context parentContext = Context.current();
    HttpRequestAndChannel request =
        HttpRequestAndChannel.create((HttpRequest) message, ctx.getChannel());
    if (!instrumenter().shouldStart(parentContext, request)) {
      ctx.sendUpstream(event);
    }

    Context context = instrumenter().start(parentContext, request);
    requestAndContextField.set(
        ctx.getChannel(), NettyServerRequestAndContext.create(request, context));

    try (Scope ignored = context.makeCurrent()) {
      ctx.sendUpstream(event);
      // the span is ended normally in HttpServerResponseTracingHandler
    } catch (Throwable throwable) {
      instrumenter().end(context, request, null, throwable);
      throw throwable;
    }
  }
}
