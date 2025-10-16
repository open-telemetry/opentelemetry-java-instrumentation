/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis.VertxRedisClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Request;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedisClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RedisConnectionImpl");
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
    public static class AdviceScope {
      private final VertxRedisClientRequest otelRequest;
      private final Context context;
      private final Scope scope;

      private AdviceScope(VertxRedisClientRequest otelRequest, Context context, Scope scope) {
        this.otelRequest = otelRequest;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(RedisConnection connection, Request request) {
        if (request == null) {
          return null;
        }

        String commandName = new String(request.command().getBytes(), StandardCharsets.UTF_8);
        if (commandName == null) {
          return null;
        }

        // Extract command arguments using RequestUtil39 (efficient approach matching 4.0)
        List<byte[]> args = io.vertx.redis.client.impl.RequestUtil39.getArgs(request);

        String connectionInfo = VertxRedisClientSingletons.getConnectionInfo(connection);
        VertxRedisClientRequest otelRequest =
            new VertxRedisClientRequest(commandName, args, connectionInfo);

        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, otelRequest)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, otelRequest);
        return new AdviceScope(otelRequest, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        if (scope == null) {
          return;
        }

        scope.close();
        instrumenter().end(context, otelRequest, null, throwable);
      }
    }

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
      List<byte[]> args = io.vertx.redis.client.impl.RequestUtil39.getArgs(request);

      String connectionInfo = VertxRedisClientSingletons.getConnectionInfo(connection);
      VertxRedisClientRequest otelRequest =
          new VertxRedisClientRequest(commandName, args, connectionInfo);

      Context parentContext = currentContext();
      
      // DEBUG: Log context information at Redis start
      io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.fromContext(parentContext);
      String parentTraceId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getTraceId() : "INVALID";
      String parentSpanId = parentSpan.getSpanContext().isValid() ? parentSpan.getSpanContext().getSpanId() : "INVALID";
      long timestamp = System.currentTimeMillis();
      Thread currentThread = Thread.currentThread();
//      System.out.println("[" + timestamp + "] [REDIS-START] Thread: " + currentThread.getName() +
//                        " (ID: " + currentThread.getId() + ", State: " + currentThread.getState() + ")" +
//                        ", Command: " + commandName +
//                        ", Parent TraceId: " + parentTraceId +
//                        ", Parent SpanId: " + parentSpanId +
//                        ", Parent Context: " + parentContext);
      
      if (!instrumenter().shouldStart(parentContext, otelRequest)) {
//        System.out.println("[REDIS-START] Instrumenter shouldStart returned false - skipping");
        return null;
      }

      Context context = instrumenter().start(parentContext, otelRequest);
      
      // DEBUG: Log new span information
      io.opentelemetry.api.trace.Span newSpan = io.opentelemetry.api.trace.Span.fromContext(context);
      String newTraceId = newSpan.getSpanContext().getTraceId();
      String newSpanId = newSpan.getSpanContext().getSpanId();
      long timestamp2 = System.currentTimeMillis();
      Thread currentThread2 = Thread.currentThread();
//      System.out.println("[" + timestamp2 + "] [REDIS-START] Thread: " + currentThread2.getName() +
//                        " (ID: " + currentThread2.getId() + ", State: " + currentThread2.getState() + ")" +
//                        " - New Span Created - TraceId: " + newTraceId +
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
