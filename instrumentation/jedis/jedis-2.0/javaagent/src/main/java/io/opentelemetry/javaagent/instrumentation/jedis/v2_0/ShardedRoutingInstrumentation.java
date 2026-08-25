/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.util.Sharded;

/**
 * Hands the configured shard list to the connection of the shard a command is routed to. The list
 * itself is rendered once when the sharded client is constructed, so routing a command only copies
 * an already rendered value.
 */
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
      JedisSingletons.attachShardedTarget(sharded, shard);
    }
  }
}
