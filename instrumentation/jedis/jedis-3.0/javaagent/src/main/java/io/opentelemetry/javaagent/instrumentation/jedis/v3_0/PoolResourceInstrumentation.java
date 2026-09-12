/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.pool2.PooledObjectFactory;
import redis.clients.jedis.util.Pool;

class PoolResourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.util.Pool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("initPool").and(takesArguments(2)), getClass().getName() + "$InitPoolAdvice");
    transformer.applyAdviceToMethod(
        named("getResource").and(takesArguments(0)), getClass().getName() + "$GetResourceAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitPoolAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This Pool<?> pool, @Advice.Argument(1) PooledObjectFactory<?> factory) {
      JedisConfiguredTargets.capturePoolFactory(pool, factory);
    }
  }

  @SuppressWarnings("unused")
  public static class GetResourceAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.This Pool<?> pool) {
      return JedisConfiguredTargets.openPoolTargetScope(pool);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Pool<?> pool,
        @Advice.Return @Nullable Object resource,
        @Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        try {
          JedisConfiguredTargets.capturePooledConnectionTarget(pool, resource);
        } finally {
          scope.close();
        }
      }
    }
  }
}
