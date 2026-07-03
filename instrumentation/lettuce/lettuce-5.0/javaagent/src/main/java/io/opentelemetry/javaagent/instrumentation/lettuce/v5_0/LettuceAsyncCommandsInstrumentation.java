/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.COMMAND_CONTEXT_KEY;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
  }

  /**
   * Exposes the caller's context under {@link
   * io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons#COMMAND_CONTEXT_KEY}
   * while a command is dispatched, so the {@code AsyncCommand} constructed by dispatch captures it
   * for completion callbacks. The command span itself is started later in {@code
   * DefaultEndpoint.write}, where batch membership is known.
   */
  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter() {
      Context parentContext = currentContext();
      return parentContext.with(COMMAND_CONTEXT_KEY, parentContext).makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
