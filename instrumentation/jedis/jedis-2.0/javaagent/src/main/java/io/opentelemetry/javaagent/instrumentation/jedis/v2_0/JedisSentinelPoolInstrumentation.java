/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.util.Pool;

class JedisSentinelPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // added in Jedis 2.2
    return namedOneOf(
        "redis.clients.jedis.JedisSentinelPool",
        "redis.clients.jedis.JedisSentinelPool$MasterListener");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, String.class)).and(takesArgument(1, Set.class)),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("initSentinels").and(takesArgument(0, Set.class)).and(takesArgument(1, String.class)),
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
      // the pool target has to be published before initSentinels starts its master listener
      // threads, and before anything is made current, so that a failure here cannot strand an
      // open scope on this thread
      RedisServerTarget target = JedisServerTargets.ofSentinels(masterName, sentinels);
      JedisSingletons.setPoolTarget(pool, target);
      Context context = JedisSingletons.configuredTargetContext(target);
      return context != null ? context.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Pool<?> pool,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
      if (throwable != null) {
        JedisSingletons.clearPoolTarget(pool);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class MasterListenerAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.FieldValue("this$0") Pool<?> pool) {
      Context context = JedisSingletons.configuredPoolTargetContext(pool);
      return context != null ? context.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
