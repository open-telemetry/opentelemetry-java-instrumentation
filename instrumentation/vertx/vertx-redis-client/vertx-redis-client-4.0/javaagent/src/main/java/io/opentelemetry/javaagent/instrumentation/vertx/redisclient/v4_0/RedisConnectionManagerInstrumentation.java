/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.impl.RedisConnectionManagerUtil;
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
    // 4.0.0 through 4.4.4, the versions built with the options the client was created with; those
    // options are mutable, so the target is captured here and read back from the manager on
    // 4.0.0 through 4.0.2 and through the thread local below on 4.0.3 through 4.4.4
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("io.vertx.redis.client.RedisOptions"))),
        getClass().getName() + "$ConstructorAdvice");
    // 4.0.3 through 4.4.4 build the connection provider here, out of reach of the manager, so the
    // thread local carries the captured target to that provider's constructor advice
    transformer.applyAdviceToMethod(
        named("connectionEndpointProvider"),
        getClass().getName() + "$ConnectionEndpointProviderAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object manager, @Advice.Argument(1) RedisOptions options) {
      RedisConnectionManagerUtil.setServerTarget(manager, VertxRedisServerTargets.of(options));
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectionEndpointProviderAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Object manager) {
      RedisConnectionManagerUtil.setServerTargetThreadLocal(manager);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      RedisConnectionManagerUtil.clearServerTargetThreadLocal();
    }
  }
}
