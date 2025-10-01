/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContexts;
import io.vertx.core.Vertx;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;

  public HttpServerRequestTracingHandler(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    Channel channel = ctx.channel();
    ServerContexts serverContexts = ServerContexts.getOrCreate(channel);

    if (!(msg instanceof HttpRequest)) {
      ServerContext serverContext = serverContexts.peekLast();
      if (serverContext == null) {
        super.channelRead(ctx, msg);
      } else {
        try (Scope ignored = serverContext.context().makeCurrent()) {
          super.channelRead(ctx, msg);
        }
      }
      return;
    }

    Context parentContext = Context.current();
    HttpRequestAndChannel request = HttpRequestAndChannel.create((HttpRequest) msg, channel);

    // ========================================
    // HTTP REQUEST SPAN INITIALIZATION
    // ========================================
//    io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.fromContext(parentContext);
//    String parentTraceId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getTraceId() : "INVALID";
//    String parentSpanId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getSpanId() : "INVALID";
    HttpRequest httpRequest = (HttpRequest) msg;
//    long timestamp = System.currentTimeMillis();
//    Thread currentThread = Thread.currentThread();

//    System.out.println("[" + timestamp + "] [HTTP-REQUEST-START] Thread: " + currentThread.getName() +
//        " (ID: " + currentThread.getId() + ", State: " + currentThread.getState() + ")" +
//        ", URI: " + httpRequest.uri() +
//        ", Method: " + httpRequest.method() +
//        ", Parent TraceId: " + parentTraceId +
//        ", Parent SpanId: " + parentSpanId +
//        ", Parent Context: " + parentContext);

    if (!instrumenter.shouldStart(parentContext, request)) {
//      System.out.println("[HTTP-INSTRUMENTATION] Instrumenter shouldStart returned false - skipping HTTP request: " + httpRequest.uri());
      super.channelRead(ctx, msg);
      return;
    }

    Context context = instrumenter.start(parentContext, request);

    // ========================================
    // TRACE CONTEXT CORRELATION SYSTEM
    // ========================================
    // Get the traceId from the newly created context
    io.opentelemetry.api.trace.Span contextSpan = io.opentelemetry.api.trace.Span.fromContext(context);
    String traceId = contextSpan.getSpanContext().getTraceId();

    // Inject the traceId as header
    httpRequest.headers().set("otel.injected_trace_context", traceId);

//    System.out.println("[TRACE-CORRELATION] Using traceId: " + traceId +
//                      ", Injected context: " + context.toString());

    // ========================================
    // VERTX CONTEXT STORAGE FOR DOWNSTREAM OPERATIONS
    // ========================================
    io.vertx.core.Context vertxContext = Vertx.currentContext();
//    Context currentOtelContext = Context.current();

//    System.out.println("[VERTX-CONTEXT-STORAGE] Vertx Context: " + vertxContext +
//                      ", New OTel Context: " + context +
//                      ", Current OTel Context: " + currentOtelContext);

    if (vertxContext != null) {
      // Store the current OpenTelemetry context in Vertx context using the traceId as key
      vertxContext.put("otel.context." + traceId, context);
      vertxContext.put("otel.context", context);
//      System.out.println("[CONTEXT-STORED] Stored OTel context in Vertx context with key 'otel.context." + traceId + "': " + context);

      // Verify storage
//      Context retrievedContext = vertxContext.get("otel.context." + traceId);
//      System.out.println("[CONTEXT-VERIFICATION] Retrieved context from Vertx: " + retrievedContext);
    } else {
//      System.out.println("[CONTEXT-STORAGE] No Vertx context available - context will not be stored");
    }



    // ========================================
    // HTTP SPAN CREATION CONFIRMATION
    // ========================================
//    io.opentelemetry.api.trace.Span newSpan = io.opentelemetry.api.trace.Span.fromContext(context);
//    String newTraceId = newSpan.getSpanContext().getTraceId();
//    String newSpanId = newSpan.getSpanContext().getSpanId();
//    long timestamp2 = System.currentTimeMillis();
//    Thread currentThread2 = Thread.currentThread();

//    System.out.println("[" + timestamp2 + "] [HTTP-SPAN-CREATED] Thread: " + currentThread2.getName() +
//        " (ID: " + currentThread2.getId() + ", State: " + currentThread2.getState() + ")" +
//        " - New HTTP Span - TraceId: " + newTraceId +
//        ", SpanId: " + newSpanId +
//        ", Context: " + context);

    // ========================================
    // CONTEXT STATE ANALYSIS
    // ========================================
//    System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread2.getName() + " - Context.current(): " + Context.current());
//    System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread2.getName() + " - Context.root(): " + Context.root());
//    System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread2.getName() + " - context == Context.current(): " + (context == Context.current()));
//    System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread2.getName() + " - context.equals(Context.current()): " + context.equals(Context.current()));
//    System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread2.getName() + " - context == Context.root(): " + (context == Context.root()));
//    System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread2.getName() + " - context.equals(Context.root()): " + context.equals(Context.root()));
    serverContexts.addLast(ServerContext.create(context, request));

    // ========================================
    // HTTP REQUEST PROCESSING WITH CONTEXT PROPAGATION
    // ========================================
    try (Scope ignored = context.makeCurrent()) {
//      System.out.println("[HTTP-PROCESSING] Processing HTTP request with active context: " + context);
      super.channelRead(ctx, msg);
    } catch (Throwable t) {
//      System.out.println("[HTTP-ERROR] Exception during HTTP processing: " + t.getMessage());
      // make sure to remove the server context on end() call
      ServerContext serverContext = serverContexts.pollLast();
      if (serverContext != null) {
        instrumenter.end(serverContext.context(), serverContext.request(), null, t);
      }
      throw t;
    }
    // span is ended normally in HttpServerResponseTracingHandler
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    // connection was closed, close all remaining requests
    ServerContexts serverContexts = ServerContexts.get(ctx.channel());

    if (serverContexts == null) {
      super.channelInactive(ctx);
      return;
    }

    ServerContext serverContext;
    while ((serverContext = serverContexts.pollFirst()) != null) {
      instrumenter.end(serverContext.context(), serverContext.request(), null, null);
    }
    super.channelInactive(ctx);
  }
}
