/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyServerSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpServerResponseTracingHandler extends SimpleChannelDownstreamHandler {

  private static final VirtualField<Channel, NettyServerRequestAndContext> requestAndContextField =
      VirtualField.find(Channel.class, NettyServerRequestAndContext.class);

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent msg) {
    NettyServerRequestAndContext requestAndContext = requestAndContextField.get(ctx.getChannel());

    if (requestAndContext == null || !(msg.getMessage() instanceof HttpResponse)) {
      ctx.sendDownstream(msg);
      return;
    }

    Context context = requestAndContext.context();
    HttpRequestAndChannel request = requestAndContext.request();
    HttpResponse response = (HttpResponse) msg.getMessage();

    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      ctx.sendDownstream(msg);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      error = NettyErrorHolder.getOrDefault(context, error);
      instrumenter().end(context, request, response, error);
    }
  }
}
