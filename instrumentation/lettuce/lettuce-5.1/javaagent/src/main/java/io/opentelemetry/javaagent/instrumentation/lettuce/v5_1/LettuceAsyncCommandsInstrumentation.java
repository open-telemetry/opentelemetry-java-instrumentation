/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceAsyncCommandsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.AbstractRedisAsyncCommands");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch").and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
        getClass().getName() + "$DispatchAdvice");
    transformer.applyAdviceToMethod(
        named("setAutoFlushCommands").and(takesArguments(1)),
        getClass().getName() + "$SetAutoFlushAdvice");
    transformer.applyAdviceToMethod(
        named("flushCommands").and(takesArguments(0)), getClass().getName() + "$FlushAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object commands,
        @Advice.Argument(0) RedisCommand<?, ?, ?> command,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (throwable == null) {
        LettuceTelemetry.capture(commands, command);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetAutoFlushAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Object commands, @Advice.Argument(0) boolean autoFlush) {
      LettuceTelemetry.setAutoFlushCommands(commands, autoFlush);
    }
  }

  @SuppressWarnings("unused")
  public static class FlushAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onEnter(@Advice.This Object commands) {
      return LettuceTelemetry.startBatch(commands);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object batch) {
      if (batch != null) {
        LettuceTelemetry.finishBatch(batch, throwable);
      }
    }
  }
}
