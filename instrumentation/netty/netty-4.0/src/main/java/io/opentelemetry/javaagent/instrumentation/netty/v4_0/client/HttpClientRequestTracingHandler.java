/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import java.net.InetSocketAddress;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    // TODO pass Context into Tracer.startSpan() and then don't need this scoping
    Scope parentScope = null;
    Context parentContext =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_CONTEXT_ATTRIBUTE_KEY).getAndRemove();
    if (parentContext != null) {
      parentScope = parentContext.makeCurrent();
    }

    HttpRequest request = (HttpRequest) msg;

    ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(Context.current());

    Span span = tracer().startSpan(request);
    NetPeerUtils.setNetPeer(span, (InetSocketAddress) ctx.channel().remoteAddress());
    ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

    try (Scope scope = tracer().startScope(span, request.headers())) {
      ctx.write(msg, prm);
    } catch (Throwable throwable) {
      tracer().endExceptionally(span, throwable);
      throw throwable;
    } finally {
      if (null != parentScope) {
        parentScope.close();
      }
    }
  }
}
