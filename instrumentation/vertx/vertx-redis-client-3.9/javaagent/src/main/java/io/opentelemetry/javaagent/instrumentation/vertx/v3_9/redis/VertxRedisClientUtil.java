/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.redis.client.Response;
import javax.annotation.Nullable;

public final class VertxRedisClientUtil {

  private static final VirtualField<Handler<?>, RequestData> requestDataField =
      VirtualField.find(Handler.class, RequestData.class);

  public static void attachRequest(
      Handler<AsyncResult<Response>> handler,
      VertxRedisClientRequest request,
      Context context,
      Context parentContext) {
    requestDataField.set(handler, new RequestData(request, context, parentContext));
  }

  @Nullable
  public static Scope endRedisSpan(
      Instrumenter<VertxRedisClientRequest, Void> instrumenter,
      Handler<AsyncResult<Response>> handler,
      @Nullable AsyncResult<Response> result,
      @Nullable Throwable throwable) {
    
    RequestData requestData = requestDataField.get(handler);
    if (requestData == null) {
      return null;
    }

    // Determine the actual throwable to report
    Throwable actualThrowable = throwable;
    if (actualThrowable == null && result != null && result.failed()) {
      actualThrowable = result.cause();
    }

    instrumenter.end(requestData.context, requestData.request, null, actualThrowable);
    return requestData.parentContext.makeCurrent();
  }

  @Nullable
  public static RequestData getRequestData(Handler<AsyncResult<Response>> handler) {
    return requestDataField.get(handler);
  }

  public static Handler<AsyncResult<Response>> wrapHandler(
      Handler<AsyncResult<Response>> originalHandler,
      VertxRedisClientRequest request,
      Context context,
      Context parentContext) {
    
    if (originalHandler == null) {
      return null;
    }

    return new ContextPreservingHandler(originalHandler, request, context, parentContext);
  }

  static class RequestData {
    final VertxRedisClientRequest request;
    final Context context;
    final Context parentContext;

    RequestData(VertxRedisClientRequest request, Context context, Context parentContext) {
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
    }
  }

  private static class ContextPreservingHandler implements Handler<AsyncResult<Response>> {
    private final Handler<AsyncResult<Response>> delegate;
    private final VertxRedisClientRequest request;
    private final Context context;
    private final Context parentContext;

    ContextPreservingHandler(
        Handler<AsyncResult<Response>> delegate,
        VertxRedisClientRequest request,
        Context context,
        Context parentContext) {
      this.delegate = delegate;
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
    }

    @Override
    public void handle(AsyncResult<Response> result) {
      // DEBUG: Log context information at Redis end
//      io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.fromContext(context);
//      io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.fromContext(parentContext);
//      long timestamp = System.currentTimeMillis();
//      Thread currentThread = Thread.currentThread();
//      System.out.println("[" + timestamp + "] [REDIS-END] Thread: " + currentThread.getName() +
//                        " (ID: " + currentThread.getId() + ", State: " + currentThread.getState() + ")" +
//                        ", Command: " + request.getCommand() +
//                        ", Current TraceId: " + currentSpan.getSpanContext().getTraceId() +
//                        ", Current SpanId: " + currentSpan.getSpanContext().getSpanId() +
//                        ", Parent TraceId: " + (parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getTraceId() : "INVALID") +
//                        ", Parent SpanId: " + (parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getSpanId() : "INVALID") +
//                        ", Success: " + result.succeeded());
      
      // End the span first
      Instrumenter<VertxRedisClientRequest, Void> instrumenter = VertxRedisClientSingletons.instrumenter();
      
      Throwable throwable = null;
      if (result.failed()) {
        throwable = result.cause();
      }
      
      instrumenter.end(context, request, null, throwable);
//      long timestamp2 = System.currentTimeMillis();
//      Thread currentThread2 = Thread.currentThread();
//      System.out.println("[" + timestamp2 + "] [REDIS-END] Thread: " + currentThread2.getName() +
//                        " (ID: " + currentThread2.getId() + ", State: " + currentThread2.getState() + ")" +
//                        " - Span ended for TraceId: " + currentSpan.getSpanContext().getTraceId());

      // Then call the original handler with the parent context
      try (Scope scope = parentContext.makeCurrent()) {
//        long timestamp3 = System.currentTimeMillis();
//        Thread currentThread3 = Thread.currentThread();
//      System.out.println("[" + timestamp3 + "] [REDIS-END] Thread: " + currentThread3.getName() +
//                        " (ID: " + currentThread3.getId() + ", State: " + currentThread3.getState() + ")" +
//                        " - Calling original handler with parent context: " + parentContext);
      
      // DEBUG: Print Context static method results at Redis end
//      System.out.println("[" + timestamp3 + "] [REDIS-END-CONTEXT-STATIC] Thread: " + currentThread3.getName() + " - Context.current(): " + Context.current());
//      System.out.println("[" + timestamp3 + "] [REDIS-END-CONTEXT-STATIC] Thread: " + currentThread3.getName() + " - Context.root(): " + Context.root());
//      System.out.println("[" + timestamp3 + "] [REDIS-END-CONTEXT-STATIC] Thread: " + currentThread3.getName() + " - parentContext == Context.current(): " + (parentContext == Context.current()));
//      System.out.println("[" + timestamp3 + "] [REDIS-END-CONTEXT-STATIC] Thread: " + currentThread3.getName() + " - parentContext.equals(Context.current()): " + parentContext.equals(Context.current()));
//      System.out.println("[" + timestamp3 + "] [REDIS-END-CONTEXT-STATIC] Thread: " + currentThread3.getName() + " - parentContext == Context.root(): " + (parentContext == Context.root()));
//      System.out.println("[" + timestamp3 + "] [REDIS-END-CONTEXT-STATIC] Thread: " + currentThread3.getName() + " - parentContext.equals(Context.root()): " + parentContext.equals(Context.root()));
      
      delegate.handle(result);
      }
    }
  }

  private VertxRedisClientUtil() {}
}
