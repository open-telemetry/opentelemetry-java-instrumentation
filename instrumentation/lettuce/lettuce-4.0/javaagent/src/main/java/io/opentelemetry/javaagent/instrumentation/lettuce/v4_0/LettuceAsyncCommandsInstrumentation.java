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
      @Nullable private final AbstractRedisAsyncCommands<?, ?> batchingCommands;
      @Nullable private final Context commandContext;
      @Nullable private final Scope scope;

      private AdviceScope(
          @Nullable AbstractRedisAsyncCommands<?, ?> batchingCommands,
          @Nullable Context commandContext,
          @Nullable Scope scope) {
        this.batchingCommands = batchingCommands;
        this.commandContext = commandContext;
        this.scope = scope;
      }

      public static AdviceScope captureForBatching(AbstractRedisAsyncCommands<?, ?> commands) {
        Context parentContext = currentContext();
        // batch spans start on flush, but AsyncCommand construction still needs the dispatch
        // caller context so callbacks run under it
        Context context = parentContext.with(COMMAND_CONTEXT_KEY, parentContext);
        return new AdviceScope(commands, null, context.makeCurrent());
      }

      public static AdviceScope startCommandSpan(Context commandContext) {
        return new AdviceScope(null, commandContext, commandContext.makeCurrent());
      }

      public void end(
          @Nullable Throwable throwable,
          RedisCommand<?, ?, ?> command,
          @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
        if (scope != null) {
          scope.close();
        }
        if (batchingCommands != null) {
          LettuceBatchContext.capture(batchingCommands, command, asyncCommand);
        } else if (commandContext != null) {
          InstrumentationPoints.afterCommand(command, commandContext, throwable, asyncCommand);
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(
        @Advice.This AbstractRedisAsyncCommands<?, ?> commands,
        @Advice.Argument(0) RedisCommand<?, ?, ?> command) {
      LettuceSingletons.attachAddress(command, commands.getConnection());
      if (LettuceBatchContext.isBatching(commands)) {
        return AdviceScope.captureForBatching(commands);
      }

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, command)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, command);
      // remember the context that called dispatch, it is used in LettuceAsyncCommandInstrumentation
      context = context.with(COMMAND_CONTEXT_KEY, parentContext);
      return AdviceScope.startCommandSpan(context);
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
      if (throwable != null && batchScope != null) {
        // Normally, BatchScope.start attaches callbacks to the command futures, and those
        // callbacks report completion to the batch scope.
        batchScope.endOne(throwable);
      }
    }
  }
}
