/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.VirtualFieldHelper.CONNECTION_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyConnectionContext;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.NettyRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientRequestAndContexts.RequestAndContexts;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpClientRequestTracingHandler extends SimpleChannelDownstreamHandler {

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
    Object message = event.getMessage();
    boolean requestWrite = message instanceof HttpRequest;
    if (!requestWrite && !(message instanceof HttpChunk)) {
      super.writeRequested(ctx, event);
      return;
    }
    NettyClientRequestAndContexts requests =
        requestWrite
            ? NettyClientRequestAndContexts.getOrCreate(ctx.getChannel())
            : NettyClientRequestAndContexts.get(ctx.getChannel());
    if (requests == null || requests.isFailed()) {
      super.writeRequested(ctx, event);
      return;
    }
    NettyClientRequestAndContexts.Write write = requests.beginWrite(requestWrite);
    if (write == null) {
      // Netty 3 executes outbound handlers on caller threads. Overlapping writes can reach the
      // socket in a different order than they entered this handler.
      requests.fail(new IllegalStateException("Overlapping HTTP writes"));
      super.writeRequested(ctx, event);
      return;
    }
    try {
      traceWrite(ctx, event, requests, write);
    } finally {
      requests.endWrite(write);
    }
  }

  private void traceWrite(
      ChannelHandlerContext ctx,
      MessageEvent event,
      NettyClientRequestAndContexts requests,
      NettyClientRequestAndContexts.Write write)
      throws Exception {
    Object message = event.getMessage();
    if (!(message instanceof HttpRequest)) {
      write(ctx, event, requests);
      return;
    }

    Context parentContext = null;
    NettyConnectionContext connectionContext = CONNECTION_CONTEXT.get(ctx.getChannel());
    if (connectionContext != null) {
      parentContext = connectionContext.remove();
    }
    if (parentContext == null) {
      parentContext = Context.current();
    }

    NettyRequest request = NettyRequest.create((HttpRequest) message, ctx.getChannel());
    Context context =
        instrumenter().shouldStart(parentContext, request)
            ? instrumenter().start(parentContext, request)
            : null;
    RequestAndContexts entry = new RequestAndContexts(parentContext, context, request);
    if (!requests.add(entry, write)) {
      entry.closed();
      super.writeRequested(ctx, event);
      return;
    }
    try (Scope ignored = (context != null ? context : parentContext).makeCurrent()) {
      write(ctx, event, requests);
    }
    // span is ended normally in HttpClientResponseTracingHandler
  }

  private void write(
      ChannelHandlerContext ctx,
      MessageEvent event,
      @Nullable NettyClientRequestAndContexts requests)
      throws Exception {
    if (requests == null || requests.peek() == null) {
      super.writeRequested(ctx, event);
      return;
    }
    event
        .getFuture()
        .addListener(
            future -> {
              if (!future.isSuccess()) {
                Throwable cause = future.getCause();
                requests.fail(cause != null ? cause : new CancellationException());
              }
            });
    try {
      super.writeRequested(ctx, event);
    } catch (Throwable t) {
      requests.fail(t);
      throw t;
    }
  }
}
