/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.BinaryJedis;
import redis.clients.util.Sharded;

class ShardedRoutingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.util.Sharded");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getShard").and(takesArguments(1)), getClass().getName() + "$GetShardAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetShardAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Sharded<?, ?> sharded, @Advice.Return @Nullable Object shard) {
      if (!(shard instanceof BinaryJedis)) {
        return;
      }
      RedisServerTarget target = JedisSingletons.shardedTarget(sharded);
      if (target != null) {
        JedisSingletons.setConnectionTarget(((BinaryJedis) shard).getClient(), target);
      }
    }
  }
}
