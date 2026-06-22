/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.RedisChannelWriter;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceDefaultEndpointInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.protocol.DefaultEndpoint");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write").and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
        getClass().getName() + "$WriteAdvice");
    transformer.applyAdviceToMethod(
        named("setAutoFlushCommands").and(takesArguments(1)),
        getClass().getName() + "$SetAutoFlushAdvice");
    transformer.applyAdviceToMethod(
        named("flushCommands").and(takesArguments(0)), getClass().getName() + "$FlushAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisChannelWriter endpoint,
        @Advice.Argument(0) RedisCommand<?, ?, ?> command,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (throwable == null) {
        LettuceBatchSupport.capture(endpoint, command);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetAutoFlushAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisChannelWriter endpoint, @Advice.Argument(0) boolean autoFlush) {
      LettuceBatchSupport.setAutoFlushCommands(endpoint, autoFlush);
    }
  }

  @SuppressWarnings("unused")
  public static class FlushAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static LettuceBatchSupport.BatchScope onEnter(@Advice.This RedisChannelWriter endpoint) {
      return LettuceBatchSupport.startBatch(endpoint);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable LettuceBatchSupport.BatchScope batch) {
      if (batch != null) {
        LettuceBatchSupport.finishBatch(batch, throwable);
      }
    }
  }
}
