/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.NettyServerSingletons.instrumenter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import javax.annotation.Nullable;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT);
    Context context = contextAttr.get();
    if (context == null) {
      ctx.write(msg, prm);
      return;
    }

    final ChannelPromise writePromise;

    if (msg instanceof LastHttpContent) {
      if (prm.isVoid()) {
        // Some frameworks don't actually listen for response completion and optimize for
        // allocations by using a singleton, unnotifiable promise. Hopefully these frameworks don't
        // have observability features or they'd be way off...
        writePromise = ctx.newPromise();
      } else {
        writePromise = prm;
      }

      // Going to finish the span after the write of the last content finishes.
      if (msg instanceof FullHttpResponse) {
        // Headers and body all sent together, we have the response information in the msg.
        writePromise.addListener(
            future -> end(ctx.channel(), (FullHttpResponse) msg, writePromise));
      } else {
        // Body sent after headers. We stored the response information in the context when
        // encountering HttpResponse (which was not FullHttpResponse since it's not
        // LastHttpContent).
        writePromise.addListener(
            future ->
                end(
                    ctx.channel(),
                    ctx.channel().attr(NettyServerSingletons.HTTP_RESPONSE).getAndRemove(),
                    writePromise));
      }
    } else {
      writePromise = prm;
      if (msg instanceof HttpResponse) {
        // Headers before body has been sent, store them to use when finishing the span.
        ctx.channel().attr(NettyServerSingletons.HTTP_RESPONSE).set((HttpResponse) msg);
      }
    }

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, writePromise);
    } catch (Throwable throwable) {
      end(ctx.channel(), null, throwable);
      throw throwable;
    }
  }

  private static void end(Channel channel, HttpResponse response, ChannelFuture future) {
    Throwable error = future.isSuccess() ? null : future.cause();
    end(channel, response, error);
  }

  // make sure to remove the server context on end() call
  private static void end(
      Channel channel, @Nullable HttpResponse response, @Nullable Throwable error) {
    Context context = channel.attr(AttributeKeys.SERVER_CONTEXT).getAndRemove();
    HttpRequestAndChannel request = channel.attr(NettyServerSingletons.HTTP_REQUEST).getAndRemove();
    error = NettyErrorHolder.getOrDefault(context, error);
    instrumenter().end(context, request, response, error);
  }
}
