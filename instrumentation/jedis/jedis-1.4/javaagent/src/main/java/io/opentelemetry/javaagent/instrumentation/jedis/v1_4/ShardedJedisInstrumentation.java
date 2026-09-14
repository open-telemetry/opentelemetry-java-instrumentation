/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.JedisShardInfo;

class ShardedJedisInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.util.Sharded");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("initialize").and(takesArgument(0, List.class)),
        getClass().getName() + "$InitializeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitializeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) @Nullable List<JedisShardInfo> shards) {
      RedisServerTarget target = JedisSingletons.createServerTarget(shards);
      if (target != null) {
        JedisSingletons.setConfiguredTarget(target);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      JedisSingletons.removeConfiguredTarget();
    }
  }
}
