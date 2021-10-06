/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.NettyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequestContexts;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpServerResponseTracingHandler extends SimpleChannelDownstreamHandler {

  private static final VirtualField<Channel, NettyRequestContexts> requestContextsField =
      VirtualField.find(Channel.class, NettyRequestContexts.class);

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent msg) {
    NettyRequestContexts requestContexts = requestContextsField.get(ctx.getChannel());

    if (requestContexts == null || !(msg.getMessage() instanceof HttpResponse)) {
      ctx.sendDownstream(msg);
      return;
    }

    Context context = requestContexts.context();
    try (Scope ignored = context.makeCurrent()) {
      ctx.sendDownstream(msg);
      tracer().end(context, (HttpResponse) msg.getMessage());
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
