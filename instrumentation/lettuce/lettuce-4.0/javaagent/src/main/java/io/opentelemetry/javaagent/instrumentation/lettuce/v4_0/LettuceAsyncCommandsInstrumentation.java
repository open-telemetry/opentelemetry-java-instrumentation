/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.COMMAND_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.lambdaworks.redis.AbstractRedisAsyncCommands;
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

class LettuceAsyncCommandsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.AbstractRedisAsyncCommands");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch")
            .and(takesArgument(0, named("com.lambdaworks.redis.protocol.RedisCommand"))),
        getClass().getName() + "$DispatchAdvice");
    transformer.applyAdviceToMethod(
        named("setAutoFlushCommands").and(takesArguments(1)),
        getClass().getName() + "$SetAutoFlushAdvice");
    transformer.applyAdviceToMethod(
        named("flushCommands").and(takesArguments(0)), getClass().getName() + "$FlushAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    public static class AdviceScope {
      private final AbstractRedisAsyncCommands<?, ?> commands;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      private final boolean captured;

      public AdviceScope(
          AbstractRedisAsyncCommands<?, ?> commands,
          @Nullable Context context,
          @Nullable Scope scope,
          boolean captured) {
        this.commands = commands;
        this.context = context;
        this.scope = scope;
        this.captured = captured;
      }

      public static AdviceScope captured(AbstractRedisAsyncCommands<?, ?> commands) {
        Context parentContext = currentContext();
        Context context = parentContext.with(COMMAND_CONTEXT_KEY, parentContext);
        return new AdviceScope(commands, null, context.makeCurrent(), true);
      }

      public void end(
          @Nullable Throwable throwable,
          RedisCommand<?, ?, ?> command,
          @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
        if (captured) {
          try {
            LettuceBatchContext.capture(commands, command, asyncCommand);
          } finally {
            if (scope != null) {
              scope.close();
            }
          }
          return;
        }
        if (scope == null || context == null) {
          return;
        }
        scope.close();
        InstrumentationPoints.afterCommand(command, context, throwable, asyncCommand);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(
        @Advice.This AbstractRedisAsyncCommands<?, ?> commands,
        @Advice.Argument(0) RedisCommand<?, ?, ?> command) {
      if (LettuceBatchContext.isCollecting(commands)) {
        return AdviceScope.captured(commands);
      }

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, command)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, command);
      // remember the context that called dispatch, it is used in LettuceAsyncCommandInstrumentation
      context = context.with(COMMAND_CONTEXT_KEY, parentContext);
      return new AdviceScope(commands, context, context.makeCurrent(), false);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) RedisCommand<?, ?, ?> command,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable AsyncCommand<?, ?, ?> asyncCommand,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, command, asyncCommand);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetAutoFlushAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractRedisAsyncCommands<?, ?> commands,
        @Advice.Argument(0) boolean autoFlush) {
      LettuceBatchContext.setCollecting(commands, !autoFlush);
    }
  }

  @SuppressWarnings("unused")
  public static class FlushAdvice {

    public static class FlushAdviceScope {
      @Nullable private final LettuceBatchContext.BatchScope batchScope;

      private FlushAdviceScope(@Nullable LettuceBatchContext.BatchScope batchScope) {
        this.batchScope = batchScope;
      }

      public static FlushAdviceScope start(AbstractRedisAsyncCommands<?, ?> commands) {
        return new FlushAdviceScope(LettuceBatchContext.flush(commands));
      }

      public void end(@Nullable Throwable throwable) {
        if (throwable != null && batchScope != null) {
          batchScope.endOne(throwable);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static FlushAdviceScope onEnter(@Advice.This AbstractRedisAsyncCommands<?, ?> commands) {
      return FlushAdviceScope.start(commands);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable FlushAdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
