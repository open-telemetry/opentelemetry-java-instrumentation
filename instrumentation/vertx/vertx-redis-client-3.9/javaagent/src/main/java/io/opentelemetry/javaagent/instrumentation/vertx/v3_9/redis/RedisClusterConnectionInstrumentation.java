/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;


import static io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis.VertxRedisClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.impl.RequestUtil39;
import io.vertx.core.Vertx;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedisClusterConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RedisClusterConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.vertx.redis.client.Request")))
            .and(takesArgument(1, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object onEnter(
        @Advice.This RedisConnection connection, 
        @Advice.Argument(0) Request request,
        @Advice.Argument(value = 1, readOnly = false) io.vertx.core.Handler<io.vertx.core.AsyncResult<io.vertx.redis.client.Response>> handler) {

      if (request == null) {
        return null;
      }

      String commandName = new String(request.command().getBytes(), StandardCharsets.UTF_8);
      if (commandName == null) {
        return null;
      }

      // Extract command arguments using RequestUtil39 (efficient approach matching 4.0)
      List<byte[]> args = RequestUtil39.getArgs(request);
//      System.out.println("manooo: here first - extracted " + args.size() + " arguments");
//      System.out.println("manooo: raw byte args: " + args);


      String connectionInfo = VertxRedisClientSingletons.getConnectionInfo(connection);
      VertxRedisClientRequest otelRequest =
          new VertxRedisClientRequest(commandName, args, connectionInfo);

      // ========================================
      // CONTEXT RESOLUTION AND VERTX INTEGRATION
      // ========================================
      Context parentContext = Context.current();
      io.vertx.core.Context vertxContext = Vertx.currentContext();
      
      // Try to retrieve stored OpenTelemetry context from Vertx context
      Context storedOtelContext =
//          null;
          vertxContext != null ? vertxContext.get("otel.context") : null;
//      System.out.println("[VERTX-CONTEXT-RETRIEVAL] Vertx Context: " + vertxContext +
//                        ", Current OTel Context: " + parentContext +
//                        ", Stored OTel Context: " + storedOtelContext);
      
      // Use stored context if current context is root and we have a stored one
      if ((parentContext == Context.root()||parentContext==null) && (storedOtelContext != null&&storedOtelContext!=Context.root())) {
//      if(storedOtelContext!=null&&storedOtelContext!=Context.root()){
        parentContext = storedOtelContext;
//        System.out.println("[CONTEXT-RESOLUTION] Using stored Vertx context as parent: " + parentContext);
      }

      // ========================================
      // SPAN CREATION DEBUGGING
      // ========================================
      io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.fromContext(parentContext);
      String parentTraceId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getTraceId() : "INVALID";
      String parentSpanId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getSpanId() : "INVALID";
      long timestamp = System.currentTimeMillis();
      Thread currentThread = Thread.currentThread();
      
//      System.out.println("[" + timestamp + "] [REDIS-SPAN-START] Thread: " + currentThread.getName() +
//                        " (ID: " + currentThread.getId() + ", State: " + currentThread.getState() + ")" +
//                        ", Command: " + commandName +
//                        ", Parent TraceId: " + parentTraceId +
//                        ", Parent SpanId: " + parentSpanId);
      
      // ========================================
      // CONTEXT STATE ANALYSIS
      // ========================================
//      System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread.getName() + " - Context.current(): " + Context.current());
//      System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread.getName() + " - Context.root(): " + Context.root());
//      System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread.getName() + " - parentContext == Context.current(): " + (parentContext == Context.current()));
//      System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread.getName() + " - parentContext.equals(Context.current()): " + parentContext.equals(Context.current()));
//      System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread.getName() + " - parentContext == Context.root(): " + (parentContext == Context.root()));
//      System.out.println("[CONTEXT-ANALYSIS] Thread: " + currentThread.getName() + " - parentContext.equals(Context.root()): " + parentContext.equals(Context.root()));
      
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
//        System.out.println("[REDIS-INSTRUMENTATION] Instrumenter shouldStart returned false - skipping Redis command: " + commandName);
        return null;
      }

      Context context = instrumenter().start(parentContext, otelRequest);
      
      // ========================================
      // NEW SPAN CONFIRMATION
      // ========================================
      io.opentelemetry.api.trace.Span newSpan = io.opentelemetry.api.trace.Span.fromContext(context);
      String newTraceId = newSpan.getSpanContext().getTraceId();
      String newSpanId = newSpan.getSpanContext().getSpanId();
      long timestamp2 = System.currentTimeMillis();
      Thread currentThread2 = Thread.currentThread();
      
//      System.out.println("[" + timestamp2 + "] [REDIS-SPAN-CREATED] Thread: " + currentThread2.getName() +
//                        " (ID: " + currentThread2.getId() + ", State: " + currentThread2.getState() + ")" +
//                        " - New Redis Span - TraceId: " + newTraceId +
//                        ", SpanId: " + newSpanId +
//                        ", Context: " + context);
      
      // Replace the handler with our context-preserving wrapper
      if (handler != null) {
        handler = VertxRedisClientUtil.wrapHandler(handler, otelRequest, context, parentContext);
      }

      // Return the original handler so we can track it
      return handler;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, 
        @Advice.Enter @Nullable Object originalHandler) {

      // If there was an immediate exception (before async execution), 
      // we need to end the span here
      if (throwable != null && originalHandler != null) {
        @SuppressWarnings("unchecked")
        io.vertx.core.Handler<io.vertx.core.AsyncResult<io.vertx.redis.client.Response>> handler = 
            (io.vertx.core.Handler<io.vertx.core.AsyncResult<io.vertx.redis.client.Response>>) originalHandler;
        
        VertxRedisClientUtil.endRedisSpan(instrumenter(), handler, null, throwable);
      }
    }
  }
}
