/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.NettyHttpServerTracer.tracer;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  private static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(HttpServerResponseTracingHandler.class, "http-response");

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Context context = tracer().getServerContext(ctx.channel());
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
        writePromise.addListener(future -> finish(context, writePromise, (FullHttpResponse) msg));
      } else {
        // Body sent after headers. We stored the response information in the context when
        // encountering HttpResponse (which was not FullHttpResponse since it's not
        // LastHttpContent).
        writePromise.addListener(
            future -> finish(context, writePromise, ctx.channel().attr(HTTP_RESPONSE).get()));
      }
    } else {
      writePromise = prm;
      if (msg instanceof HttpResponse) {
        // Headers before body has been sent, store them to use when finishing the span.
        ctx.channel().attr(HTTP_RESPONSE).set((HttpResponse) msg);
      }
    }

    try (Scope ignored = context.makeCurrent()) {
      ctx.write(msg, writePromise);
    } catch (Throwable throwable) {
      tracer().endExceptionally(context, throwable);
      throw throwable;
    }
  }

  private static void finish(Context context, ChannelFuture future, HttpResponse response) {
    if (future.isSuccess()) {
      tracer().end(context, response);
    } else {
      tracer().endExceptionally(context, future.cause());
    }
  }
}
