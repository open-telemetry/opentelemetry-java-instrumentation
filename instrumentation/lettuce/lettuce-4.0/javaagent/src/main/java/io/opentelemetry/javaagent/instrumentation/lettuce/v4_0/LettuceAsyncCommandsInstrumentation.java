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
      private final boolean batching;

      public AdviceScope(
          AbstractRedisAsyncCommands<?, ?> commands,
          @Nullable Context context,
          @Nullable Scope scope,
          boolean batching) {
        this.commands = commands;
        this.context = context;
        this.scope = scope;
        this.batching = batching;
      }

      public static AdviceScope batching(AbstractRedisAsyncCommands<?, ?> commands) {
        Context parentContext = currentContext();
        Context context = parentContext.with(COMMAND_CONTEXT_KEY, parentContext);
        return new AdviceScope(commands, null, context.makeCurrent(), true);
      }

      public void end(
          @Nullable Throwable throwable,
          RedisCommand<?, ?, ?> command,
          @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
        if (scope != null) {
          scope.close();
        }
        if (batching) {
          LettuceBatchContext.capture(commands, command, asyncCommand);
        } else if (context != null) {
          InstrumentationPoints.afterCommand(command, context, throwable, asyncCommand);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(
        @Advice.This AbstractRedisAsyncCommands<?, ?> commands,
        @Advice.Argument(0) RedisCommand<?, ?, ?> command) {
      if (LettuceBatchContext.isBatching(commands)) {
        return AdviceScope.batching(commands);
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
      LettuceBatchContext.setBatching(commands, !autoFlush);
    }
  }

  @SuppressWarnings("unused")
  public static class FlushAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static LettuceBatchContext.BatchScope onEnter(
        @Advice.This AbstractRedisAsyncCommands<?, ?> commands) {
      return LettuceBatchContext.flush(commands);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable LettuceBatchContext.BatchScope batchScope) {
      // the batch span is normally ended once all of its commands complete (BatchScope.endOne);
      // end the batch early here if flushCommands() itself throws
      if (throwable != null && batchScope != null) {
        batchScope.endOne(throwable);
      }
    }
  }
}
