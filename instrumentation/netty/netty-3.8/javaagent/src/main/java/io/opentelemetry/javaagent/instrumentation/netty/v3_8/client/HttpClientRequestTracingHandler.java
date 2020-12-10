/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.tracer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpClientRequestTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent msg) {
    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);
    HttpClientOperation operation = tracer().startOperation(ctx, msg, channelTraceContext);
    channelTraceContext.setOperation(operation);
    try (Scope ignored = operation.makeCurrent()) {
      ctx.sendDownstream(msg);
    } catch (Throwable throwable) {
      tracer().endExceptionally(operation, throwable);
      throw throwable;
    }
  }
}
