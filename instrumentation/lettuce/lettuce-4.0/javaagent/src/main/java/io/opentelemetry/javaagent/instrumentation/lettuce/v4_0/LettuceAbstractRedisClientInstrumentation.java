/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.lambdaworks.redis.AbstractRedisClient;
import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.cluster.RedisClusterClient;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceAbstractRedisClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.AbstractRedisClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("connectAsyncImpl")
            .and(takesArgument(1, named("com.lambdaworks.redis.RedisChannelHandler"))),
        getClass().getName() + "$AttachClusterConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class AttachClusterConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This AbstractRedisClient client,
        @Advice.Argument(1) RedisChannelHandler<?, ?> connection) {
      if (client instanceof RedisClusterClient) {
        LettuceServerTargets.copy((RedisClusterClient) client, connection);
      }
    }
  }
}
