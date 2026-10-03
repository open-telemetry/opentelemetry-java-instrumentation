/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.impl.RedisConnectionManagerUtil;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class RedisConnectionManagerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RedisConnectionManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(5))
            .and(takesArgument(3, named("io.vertx.redis.client.RedisConnectOptions"))),
        getClass().getName() + "$ConstructorWithOptionsAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(5)).and(takesArgument(3, Supplier.class)),
        getClass().getName() + "$ConstructorWithSupplierAdvice");
    transformer.applyAdviceToMethod(
        named("connectionEndpointProvider"),
        getClass().getName() + "$ConnectionEndpointProviderAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithOptionsAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object manager, @Advice.Argument(3) RedisConnectOptions options) {
      RedisConnectionManagerUtil.setServerTarget(manager, VertxRedisServerTargets.of(options));
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithSupplierAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object manager, @Advice.Argument(3) Supplier<?> optionsSupplier) {
      RedisConnectionManagerUtil.setServerTarget(
          manager, VertxRedisServerTargets.ofConstantSupplier(optionsSupplier));
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectionEndpointProviderAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static RedisServerTarget onEnter(@Advice.This Object manager) {
      return RedisConnectionManagerUtil.currentServerTarget()
          .set(RedisConnectionManagerUtil.getServerTarget(manager));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable RedisServerTarget previous) {
      RedisConnectionManagerUtil.currentServerTarget().restore(previous);
    }
  }
}
