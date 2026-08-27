/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.CLUSTER_CLIENT_TARGET;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.CONNECTION_TARGET;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.cluster.RedisClusterClient;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceClusterClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.cluster.RedisClusterClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // every constructor ends up storing the seed URIs, and none of them agrees on an argument list
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("connectClusterImpl")
            .and(
                returns(named("com.lambdaworks.redis.cluster.StatefulRedisClusterConnectionImpl"))),
        getClass().getName() + "$AttachConnectionAdvice");
    transformer.applyAdviceToMethod(
        named("connectToNode")
            .and(takesArgument(0, named("com.lambdaworks.redis.codec.RedisCodec")))
            .and(returns(named("com.lambdaworks.redis.api.StatefulRedisConnection"))),
        getClass().getName() + "$AttachConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client,
        @Advice.FieldValue("initialUris") @Nullable Iterable<RedisURI> initialUris) {
      // a RedisURI is mutable, so the seed list is rendered here and kept immutable
      CLUSTER_CLIENT_TARGET.set(client, LettuceServerTargets.ofUris(initialUris));
    }
  }

  @SuppressWarnings("unused")
  public static class AttachConnectionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client, @Advice.Return @Nullable Object connection) {
      RedisServerTarget target = CLUSTER_CLIENT_TARGET.get(client);
      if (target != null && connection instanceof RedisChannelHandler) {
        CONNECTION_TARGET.set((RedisChannelHandler<?, ?>) connection, target);
      }
    }
  }
}
