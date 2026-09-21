/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.util.Pool;

class JedisPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.JedisPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, HostAndPort.class)),
        getClass().getName() + "$FirstArgumentAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, HostAndPort.class)),
        getClass().getName() + "$SecondArgumentAdvice");
  }

  @SuppressWarnings("unused")
  public static class FirstArgumentAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Pool<?> pool, @Advice.Argument(0) @Nullable HostAndPort endpoint) {
      JedisConfiguredTargets.setPoolTarget(
          pool, JedisConfiguredTargets.hostAndPortTarget(endpoint));
    }
  }

  @SuppressWarnings("unused")
  public static class SecondArgumentAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Pool<?> pool, @Advice.Argument(1) @Nullable HostAndPort endpoint) {
      JedisConfiguredTargets.setPoolTarget(
          pool, JedisConfiguredTargets.hostAndPortTarget(endpoint));
    }
  }
}
