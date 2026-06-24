/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.COMMAND_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.clearContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.getContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.setContext;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceReactiveCommandInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.ReactiveCommandDispatcher$ObservableCommand");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(3))
            .and(takesArgument(0, named("com.lambdaworks.redis.protocol.RedisCommand")))
            .and(takesArgument(1, named("rx.Subscriber")))
            .and(takesArgument(2, boolean.class)),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("complete", "cancel")
            .and(takesArguments(0))
            .or(
                named("completeExceptionally")
                    .and(takesArguments(1))
                    .and(takesArgument(0, Throwable.class))),
        getClass().getName() + "$TerminalAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This RedisCommand<?, ?, ?> command) {
      Context context = Java8BytecodeBridge.currentContext();
      if (context.get(COMMAND_CONTEXT_KEY) != null) {
        setContext(command, context);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class TerminalAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This RedisCommand<?, ?, ?> command,
        @Advice.Origin("#m") String methodName,
        @Advice.Argument(value = 0, optional = true) @Nullable Throwable commandError) {
      Context context = getContext(command);
      if (context == null) {
        return null;
      }

      clearContext(command);
      InstrumentationPoints.endReactiveCommand(command, context, methodName, commandError);
      context = context.get(COMMAND_CONTEXT_KEY);
      return context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
