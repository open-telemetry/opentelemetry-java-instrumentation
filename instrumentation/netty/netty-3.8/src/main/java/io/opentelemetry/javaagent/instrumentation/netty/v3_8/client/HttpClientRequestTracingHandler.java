/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.TRACER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import java.net.InetSocketAddress;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpClientRequestTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent msg) {
    if (!(msg.getMessage() instanceof HttpRequest)) {
      ctx.sendDownstream(msg);
      return;
    }

    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    // TODO pass Context into Tracer.startSpan() and then don't need this scoping
    Scope parentScope = null;
    Context parentContext = channelTraceContext.getConnectionContext();
    if (parentContext != null) {
      parentScope = parentContext.makeCurrent();
      channelTraceContext.setConnectionContext(null);
    }
    channelTraceContext.setClientParentContext(Context.current());

    HttpRequest request = (HttpRequest) msg.getMessage();

    Span span = TRACER.startSpan(request);
    NetPeerUtils.setNetPeer(span, (InetSocketAddress) ctx.getChannel().getRemoteAddress());
    channelTraceContext.setClientSpan(span);

    try (Scope ignored = TRACER.startScope(span, request.headers())) {
      ctx.sendDownstream(msg);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    } finally {
      if (parentScope != null) {
        parentScope.close();
      }
    }
  }
}
