/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceCommandWrapperInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.lambdaworks.redis.protocol.CommandWrapper",
        "com.lambdaworks.redis.cluster.ClusterCommand");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("com.lambdaworks.redis.protocol.RedisCommand"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("complete", "completeExceptionally", "cancel"),
        getClass().getName() + "$TerminalAdvice");
  }

  public static class ConstructorAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisCommand<?, ?, ?> wrapper,
        @Advice.Argument(0) RedisCommand<?, ?, ?> command) {
      LettuceSingletons.linkCommandPeer(wrapper, command);
    }
  }

  public static class TerminalAdvice {

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This RedisCommand<?, ?, ?> wrapper) {
      if (wrapper.isDone()) {
        LettuceSingletons.clearCommandPeer(wrapper);
      }
    }
  }
}
