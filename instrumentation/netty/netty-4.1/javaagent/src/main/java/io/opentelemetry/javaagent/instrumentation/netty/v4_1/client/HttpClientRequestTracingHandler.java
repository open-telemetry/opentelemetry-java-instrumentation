/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import java.net.InetSocketAddress;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }
    HttpRequest request = (HttpRequest) msg;

    Context parentContext = ctx.channel().attr(AttributeKeys.CONNECT_CONTEXT).getAndRemove();
    if (parentContext == null) {
      parentContext = Context.current();
    }

    if (!tracer().shouldStartSpan(parentContext, request)) {
      ctx.write(msg, prm);
      return;
    }

    ctx.channel().attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(parentContext);

    Context context = tracer().startSpan(parentContext, request, request.headers());
    // TODO (trask) move this setNetPeer() call into the Tracer
    NetPeerUtils.INSTANCE.setNetPeer(
        Span.fromContext(context), (InetSocketAddress) ctx.channel().remoteAddress());
    ctx.channel().attr(AttributeKeys.CLIENT_SPAN).set(context);

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
      // span is ended normally in HttpClientResponseTracingHandler
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }
}
