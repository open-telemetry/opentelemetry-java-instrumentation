/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LettuceAsyncCommandsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.AbstractRedisAsyncCommands");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("dispatch"))
            .and(takesArgument(0, named("com.lambdaworks.redis.protocol.RedisCommand"))),
        LettuceAsyncCommandsInstrumentation.class.getName() + "$DispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      public AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      public void end(
          @Nullable Throwable throwable,
          RedisCommand<?, ?, ?> command,
          AsyncCommand<?, ?, ?> asyncCommand) {
        scope.close();
        InstrumentationPoints.afterCommand(command, context, throwable, asyncCommand);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) RedisCommand<?, ?, ?> command) {

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, command)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, command);
      // remember the context that called dispatch, it is used in LettuceAsyncCommandInstrumentation
      context = context.with(LettuceSingletons.COMMAND_CONTEXT_KEY, parentContext);
      return new AdviceScope(context, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) RedisCommand<?, ?, ?> command,
        @Advice.Thrown Throwable throwable,
        @Advice.Return AsyncCommand<?, ?, ?> asyncCommand,
        @Advice.Enter AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, command, asyncCommand);
      }
    }
  }
}
