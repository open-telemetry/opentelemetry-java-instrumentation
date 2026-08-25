/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.util.Pool;

/**
 * Captures the sentinels and master a Sentinel backed pool was configured with. Sentinel pools were
 * added in jedis 2.2, so on earlier versions this matches nothing and only the pool itself is
 * instrumented.
 */
class JedisSentinelPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.JedisSentinelPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("java.util.Set"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Pool<?> pool,
        @Advice.Argument(0) @Nullable String masterName,
        @Advice.Argument(1) @Nullable Set<?> sentinels) {
      JedisSingletons.setPoolTarget(pool, JedisServerTargets.ofSentinels(masterName, sentinels));
    }
  }
}
