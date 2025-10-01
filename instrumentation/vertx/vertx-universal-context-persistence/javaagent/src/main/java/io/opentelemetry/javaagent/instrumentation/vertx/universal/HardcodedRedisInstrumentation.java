/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Hardcoded instrumentation for Redis send method to test our basic framework. */
public class HardcodedRedisInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.redis.client.RedisConnection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    System.out.println(
        "HARDCODED-REDIS-TRANSFORM: Targeting RedisConnection interface send method");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.vertx.redis.client.Request")))
            .and(takesArgument(1, named("io.vertx.core.Handler"))),
        HardcodedRedisAdvice.class.getName());
  }

  public static class HardcodedRedisAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object onEnter(
        @Advice.This io.vertx.redis.client.RedisConnection connection,
        @Advice.Argument(0) io.vertx.redis.client.Request request,
        @Advice.Argument(value = 1, readOnly = false)
            io.vertx.core.Handler<io.vertx.core.AsyncResult<io.vertx.redis.client.Response>>
                handler) {

      System.out.println("HARDCODED-REDIS-ENTER: Redis send method called");
      System.out.println(
          "HARDCODED-REDIS-ENTER: Handler type: "
              + (handler != null ? handler.getClass().getName() : "null"));

      Context currentContext = Context.current();
      System.out.println("HARDCODED-REDIS-ENTER: Current context: " + currentContext);

      if (handler != null) {
        System.out.println("HARDCODED-REDIS-ENTER: Handler is a Vertx Handler - wrapping it");
        // Wrap the handler with context preservation
        handler = new UniversalContextPreservingHandler<>(handler);
        System.out.println("HARDCODED-REDIS-ENTER: Handler wrapped successfully");
      }

      return handler; // Return for tracking
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Enter Object wrappedHandler) {
      System.out.println("HARDCODED-REDIS-EXIT: Redis send method completed");
      System.out.println(
          "HARDCODED-REDIS-EXIT: Wrapped handler: "
              + (wrappedHandler != null ? wrappedHandler.getClass().getName() : "null"));
    }
  }
}
