/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvents;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import java.util.Deque;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  private static final AttributeKey<HttpResponse> HTTP_SERVER_RESPONSE =
      AttributeKey.valueOf(HttpServerResponseTracingHandler.class, "http-server-response");

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;
  private final HttpServerResponseBeforeCommitHandler beforeCommitHandler;

  public HttpServerResponseTracingHandler(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter,
      HttpServerResponseBeforeCommitHandler beforeCommitHandler) {
    this.instrumenter = instrumenter;
    this.beforeCommitHandler = beforeCommitHandler;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
    Attribute<Deque<ServerContext>> serverContextAttr =
        ctx.channel().attr(AttributeKeys.SERVER_CONTEXT);

    Deque<ServerContext> serverContexts = serverContextAttr.get();
    ServerContext serverContext = serverContexts != null ? serverContexts.peekFirst() : null;

    if (serverContext == null) {
      super.write(ctx, msg, prm);
      return;
    }

    ChannelPromise writePromise;

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
        FullHttpResponse response = (FullHttpResponse) msg;
        if (response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
          ProtocolSpecificEvents.SWITCHING_PROTOCOLS.addEvent(
              serverContext.context(), serverContext.request().request(), response);
        } else {
          // Headers and body all sent together, we have the response information in the msg.
          beforeCommitHandler.handle(serverContext.context(), (HttpResponse) msg);
          serverContexts.removeFirst();
          writePromise.addListener(
              future ->
                  end(
                      serverContext.context(),
                      serverContext.request(),
                      (FullHttpResponse) msg,
                      writePromise));
        }
      } else {
        HttpResponse responseTest = ctx.channel().attr(HTTP_SERVER_RESPONSE).get();
        if (responseTest == null
            || !responseTest.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
          // Body sent after headers. We stored the response information in the context when
          // encountering HttpResponse (which was not FullHttpResponse since it's not
          // LastHttpContent).
          serverContexts.removeFirst();
          HttpResponse response = ctx.channel().attr(HTTP_SERVER_RESPONSE).getAndSet(null);
          writePromise.addListener(
              future ->
                  end(serverContext.context(), serverContext.request(), response, writePromise));
        }
      }
    } else {
      writePromise = prm;
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;
        if (response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
          ProtocolSpecificEvents.SWITCHING_PROTOCOLS.addEvent(
              serverContext.context(), serverContext.request().request(), response);
        } else {
          // Headers before body has been sent, store them to use when finishing the span.
          beforeCommitHandler.handle(serverContext.context(), response);
          ctx.channel().attr(HTTP_SERVER_RESPONSE).set(response);
        }
      }
    }

    try (Scope ignored = serverContext.context().makeCurrent()) {
      super.write(ctx, msg, writePromise);
    } catch (Throwable throwable) {
      serverContexts.removeFirst();
      end(serverContext.context(), serverContext.request(), null, throwable);
      throw throwable;
    }
  }

  private void end(
      Context context, HttpRequestAndChannel request, HttpResponse response, ChannelFuture future) {
    Throwable error = future.isSuccess() ? null : future.cause();
    end(context, request, response, error);
  }

  private void end(
      Context context,
      HttpRequestAndChannel request,
      @Nullable HttpResponse response,
      @Nullable Throwable error) {
    error = NettyErrorHolder.getOrDefault(context, error);
    instrumenter.end(context, request, response, error);
  }
}
