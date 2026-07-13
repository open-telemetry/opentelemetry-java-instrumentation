/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.COMMAND_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.REACTIVE_DISPATCHER_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.lambdaworks.redis.ReactiveCommandDispatcher;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceReactiveCommandDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.ReactiveCommandDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("call").and(takesArguments(1)).and(takesArgument(0, named("rx.Subscriber"))),
        getClass().getName() + "$CallAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This ReactiveCommandDispatcher<?, ?, ?> dispatcher) {
      REACTIVE_DISPATCHER_CONTEXT.set(dispatcher, currentContext());
    }
  }

  @SuppressWarnings("unused")
  public static class CallAdvice {

    public static class AdviceScope {
      private final RedisCommand<?, ?, ?> command;
      private final Context context;
      private final Scope scope;

      public AdviceScope(RedisCommand<?, ?, ?> command, Context context) {
        this.command = command;
        this.context = context;
        this.scope = context.makeCurrent();
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (throwable != null) {
          instrumenter().end(context, command, null, throwable);
        } else if (!InstrumentationPoints.expectsResponse(command)) {
          instrumenter().end(context, command, null, null);
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This ReactiveCommandDispatcher<?, ?, ?> dispatcher,
        @Advice.FieldValue(value = "command") @Nullable RedisCommand<?, ?, ?> command,
        @Advice.FieldValue("commandSupplier")
            Supplier<? extends RedisCommand<?, ?, ?>> commandSupplier) {
      Context parentContext = REACTIVE_DISPATCHER_CONTEXT.get(dispatcher);
      if (parentContext == null) {
        return null;
      }
      RedisCommand<?, ?, ?> otelCommand = command == null ? commandSupplier.get() : command;
      if (!instrumenter().shouldStart(parentContext, otelCommand)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, otelCommand);
      // remember the context that called dispatch, it is used in
      // LettuceObservableCommandInstrumentation
      context = context.with(COMMAND_CONTEXT_KEY, parentContext);
      return new AdviceScope(otelCommand, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
