/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.HttpServerRequestTracingHandler.HTTP_SERVER_REQUEST;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.NettyServerSingletons.instrumenter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.AttributeKeys;
import javax.annotation.Nullable;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
    if (context == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    customizeResponse(context, (HttpResponse) msg);

    ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).remove();
    NettyCommonRequest request = ctx.channel().attr(HTTP_SERVER_REQUEST).getAndRemove();
    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, prm);
    } catch (Throwable t) {
      end(context, request, (HttpResponse) msg, t);
      throw t;
    }
    end(context, request, (HttpResponse) msg, null);
  }

  // make sure to remove the server context on end() call
  private static void end(
      Context context,
      NettyCommonRequest request,
      HttpResponse response,
      @Nullable Throwable error) {
    error = NettyErrorHolder.getOrDefault(context, error);
    instrumenter().end(context, request, response, error);
  }

  private static void customizeResponse(Context context, HttpResponse response) {
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(context, response, NettyHttpResponseMutator.INSTANCE);
  }
}
