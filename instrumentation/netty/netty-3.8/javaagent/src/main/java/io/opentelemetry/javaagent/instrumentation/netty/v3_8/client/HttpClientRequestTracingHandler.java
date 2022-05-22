/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyConnectionContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  private static final VirtualField<Channel, NettyConnectionContext> connectionContextField =
      VirtualField.find(Channel.class, NettyConnectionContext.class);
  private static final VirtualField<Channel, NettyClientRequestAndContexts> requestContextsField =
      VirtualField.find(Channel.class, NettyClientRequestAndContexts.class);

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

    HttpRequestAndChannel request =
        HttpRequestAndChannel.create((HttpRequest) message, ctx.getChannel());
    if (!instrumenter().shouldStart(parentContext, request)) {
      ctx.sendDownstream(event);
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    requestContextsField.set(
        ctx.getChannel(), NettyClientRequestAndContexts.create(parentContext, context, request));

    try (Scope ignored = context.makeCurrent()) {
      ctx.sendDownstream(event);
      // span is ended normally in HttpClientResponseTracingHandler
    } catch (Throwable throwable) {
      instrumenter().end(context, request, null, throwable);
      throw throwable;
    }
  }
}
