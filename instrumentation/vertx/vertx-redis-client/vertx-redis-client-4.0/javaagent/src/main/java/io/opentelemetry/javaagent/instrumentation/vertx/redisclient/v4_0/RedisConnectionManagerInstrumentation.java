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
    // 4.0.0 through 4.4.4, the versions built with the options the client was created with; the
    // target is only read back on 4.0.0 through 4.0.2, where the connection provider cannot reach
    // those options on its own
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, named("io.vertx.redis.client.RedisOptions"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object manager, @Advice.Argument(1) RedisOptions options) {
      // RedisConnectionManager is not visible from this package
      RedisConnectionManagerUtil.setServerTarget(manager, VertxRedisServerTargets.of(options));
    }
  }
}
