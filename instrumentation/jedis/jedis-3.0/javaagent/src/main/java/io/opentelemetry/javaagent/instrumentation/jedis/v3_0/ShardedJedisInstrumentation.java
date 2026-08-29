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
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.util.Sharded;

class ShardedJedisInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("redis.clients.jedis.BinaryShardedJedis", "redis.clients.jedis.util.Sharded");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("initialize").and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$InitializeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Sharded<?, ?> sharded,
        @Advice.Argument(0) @Nullable List<JedisShardInfo> shards) {
      JedisSingletons.setShardedTarget(sharded, JedisServerTargets.ofShards(shards));
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) @Nullable List<JedisShardInfo> shards) {
      return JedisSingletons.openConfiguredTargetScope(JedisServerTargets.ofShards(shards));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
