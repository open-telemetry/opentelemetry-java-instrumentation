/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.util.Pool;

class JedisSentinelPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "redis.clients.jedis.JedisSentinelPool",
        "redis.clients.jedis.JedisSentinelPool$MasterListener");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("java.util.Set"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("initSentinels")
            .and(takesArgument(0, named("java.util.Set")))
            .and(takesArgument(1, named("java.lang.String"))),
        getClass().getName() + "$InitializeAdvice");
    transformer.applyAdviceToMethod(named("run"), getClass().getName() + "$MasterListenerAdvice");
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

  @SuppressWarnings("unused")
  public static class InitializeAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This Pool<?> pool,
        @Advice.Argument(0) @Nullable Set<?> sentinels,
        @Advice.Argument(1) @Nullable String masterName) {
      JedisSingletons.setPoolTarget(pool, JedisServerTargets.ofSentinels(masterName, sentinels));
      return JedisSingletons.openPoolTargetScope(pool);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class MasterListenerAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.FieldValue("this$0") Pool<?> pool) {
      return JedisSingletons.openPoolTargetScope(pool);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
