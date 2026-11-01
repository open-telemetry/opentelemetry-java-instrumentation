/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.RedisClientActorLike;

class RediscalaClientActorLikeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.RedisClientActorLike");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("reconnect"))
            .and(takesArguments(String.class, int.class))
            .and(returns(void.class)),
        getClass().getName() + "$ReconnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This RedisClientActorLike client) {
      RediscalaServerTargets.captureClientTarget(client);
    }
  }

  @SuppressWarnings("unused")
  public static class ReconnectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter(
        @Advice.This RedisClientActorLike client,
        @Advice.Argument(0) String host,
        @Advice.Argument(1) int port) {
      return RediscalaServerTargets.clientConfigurationChanged(client, host, port);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClientActorLike client,
        @Advice.Argument(0) String host,
        @Advice.Argument(1) int port,
        @Advice.Enter boolean configurationChanged,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (configurationChanged && throwable == null) {
        RediscalaServerTargets.updateClientTarget(client, host, port);
      }
    }
  }
}
