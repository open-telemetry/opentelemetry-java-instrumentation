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
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.common.v4_0.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvent;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContexts;
import javax.annotation.Nullable;
import io.vertx.core.Vertx;
//import io.vertx.core.impl.EventLoopContext;
/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  private static final AttributeKey<HttpResponse> HTTP_SERVER_RESPONSE =
      AttributeKey.valueOf(HttpServerResponseTracingHandler.class, "http-server-response");

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;
  private final HttpServerResponseBeforeCommitHandler beforeCommitHandler;
  private final ProtocolEventHandler eventHandler;

  public HttpServerResponseTracingHandler(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter,
      HttpServerResponseBeforeCommitHandler beforeCommitHandler,
      ProtocolEventHandler eventHandler) {
    this.instrumenter = instrumenter;
    this.beforeCommitHandler = beforeCommitHandler;
    this.eventHandler = eventHandler;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
    ServerContexts serverContexts = ServerContexts.get(ctx.channel());
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
          eventHandler.handle(
              ProtocolSpecificEvent.SWITCHING_PROTOCOLS,
              serverContext.context(),
              serverContext.request().request(),
              response);
        } else {
          // Headers and body all sent together, we have the response information in the msg.
          beforeCommitHandler.handle(serverContext.context(), (HttpResponse) msg);
          serverContexts.pollFirst();
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
          serverContexts.pollFirst();
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
          eventHandler.handle(
              ProtocolSpecificEvent.SWITCHING_PROTOCOLS,
              serverContext.context(),
              serverContext.request().request(),
              response);
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
      serverContexts.pollFirst();
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

    // DEBUG: Log HTTP span end information
    io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.fromContext(context);
    String currentTraceId = currentSpan.getSpanContext().getTraceId();
//    String currentSpanId = currentSpan.getSpanContext().getSpanId();
//    String status = response != null ? response.status().toString() : "null";
//    long timestamp = System.currentTimeMillis();
//    Thread currentThread = Thread.currentThread();
//    System.out.println("[" + timestamp + "] [HTTP-END] Thread: " + currentThread.getName() +
//        " (ID: " + currentThread.getId() + ", State: " + currentThread.getState() + ")" +
//        ", TraceId: " + currentTraceId +
//        ", SpanId: " + currentSpanId +
//        ", Status: " + status +
//        ", Error: " + (error != null ? error.getMessage() : "none") +
//        ", Context: " + context);

    error = NettyErrorHolder.getOrDefault(context, error);
    instrumenter.end(context, request, response, error);

    io.vertx.core.Context vertxContext = Vertx.currentContext();

//    EventLoopContext eventLoopContext = (EventLoopContext)Vertx.currentContext();
    if (vertxContext != null) {
      // Store the current OpenTelemetry context in Vertx context for downstream operations
      vertxContext.remove("otel.context." + currentTraceId);
      vertxContext.remove("otel.context");
//      System.out.println("["+Thread.currentThread().getName()+"][SUPERMANU] vertx context count: " + eventLoopContext.contextData().keySet().size()+ " \n keys:::"+eventLoopContext.contextData().keySet());
      // Verify storage
//      Context retrievedContext =
//      null;
//          vertxContext.get("otel.context");
//      System.out.println("[CONTEXT-VERIFICATION] Retrieved context from Vertx: " + retrievedContext);
    }


//    long timestamp2 = System.currentTimeMillis();
//    Thread currentThread2 = Thread.currentThread();
//    System.out.println("[" + timestamp2 + "] [HTTP-END] Thread: " + currentThread2.getName() +
//        " (ID: " + currentThread2.getId() + ", State: " + currentThread2.getState() + ")" +
//        " - HTTP Span ended for TraceId: " + currentTraceId);

    // DEBUG: Print Context static method results at HTTP end
//    System.out.println("[" + timestamp2 + "] [HTTP-END-CONTEXT-STATIC] Thread: " + currentThread2.getName() + " - Context.current(): " + Context.current());
//    System.out.println("[" + timestamp2 + "] [HTTP-END-CONTEXT-STATIC] Thread: " + currentThread2.getName() + " - Context.root(): " + Context.root());
//    System.out.println("[" + timestamp2 + "] [HTTP-END-CONTEXT-STATIC] Thread: " + currentThread2.getName() + " - context == Context.current(): " + (context == Context.current()));
//    System.out.println("[" + timestamp2 + "] [HTTP-END-CONTEXT-STATIC] Thread: " + currentThread2.getName() + " - context.equals(Context.current()): " + context.equals(Context.current()));
//    System.out.println("[" + timestamp2 + "] [HTTP-END-CONTEXT-STATIC] Thread: " + currentThread2.getName() + " - context == Context.root(): " + (context == Context.root()));
//    System.out.println("[" + timestamp2 + "] [HTTP-END-CONTEXT-STATIC] Thread: " + currentThread2.getName() + " - context.equals(Context.root()): " + context.equals(Context.root()));
  }
}
