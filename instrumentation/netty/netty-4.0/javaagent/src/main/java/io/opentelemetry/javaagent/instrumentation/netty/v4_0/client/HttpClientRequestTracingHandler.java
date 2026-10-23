/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.NettyClientSingletons.instrumenter;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  public static final AttributeKey<NettyCommonRequest> HTTP_CLIENT_REQUEST =
      AttributeKeys.attributeKey(AttributeKeys.class.getName() + ".http-client-request");

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
    HttpClientRequestQueue queue = HttpClientRequestQueue.get(ctx.channel());
    if (queue.isFailed()) {
      super.write(ctx, msg, prm);
      return;
    }

    Context parentContext = Context.current();
    Context context = parentContext;
    if (msg instanceof HttpRequest) {
      NettyCommonRequest request = NettyCommonRequest.create((HttpRequest) msg, ctx.channel());
      Context clientContext =
          instrumenter().shouldStart(parentContext, request)
              ? instrumenter().start(parentContext, request)
              : null;
      queue.add(new HttpClientRequestQueue.Request(request, parentContext, clientContext));
      if (clientContext != null) {
        context = clientContext;
      }
    } else if (queue.peek() == null) {
      super.write(ctx, msg, prm);
      return;
    }

    // Void promises cannot notify listeners. Use a regular promise without changing application
    // visibility of a failure, which a void promise normally forwards to exceptionCaught.
    // isVoid()/unvoid() are not available in the earliest supported Netty version.
    ChannelPromise promise =
        prm == ctx.voidPromise()
                || prm.getClass().getName().equals("io.netty.channel.VoidChannelPromise")
            ? ctx.newPromise()
            : prm;
    promise.addListener(
        (ChannelFutureListener)
            future -> {
              if (!future.isSuccess()) {
                queue.fail(future.cause());
                if (promise != prm) {
                  prm.setFailure(future.cause());
                }
              }
            });
    try (Scope ignored = context.makeCurrent()) {
      super.write(ctx, msg, promise);
    } catch (Throwable t) {
      queue.fail(t);
      throw t;
    }
    // span is ended normally in HttpClientResponseTracingHandler
  }
}
