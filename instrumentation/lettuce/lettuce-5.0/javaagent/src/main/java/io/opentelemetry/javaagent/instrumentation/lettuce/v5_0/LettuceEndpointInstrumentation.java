/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.CommandWrapper;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments {@code DefaultEndpoint} (the {@code RedisChannelWriter}), the single point where every
 * command is written regardless of whether the application drives auto-flush through the commands
 * object (older lettuce) or the connection (newer lettuce). Per-command spans and auto-flush batch
 * aggregation are both decided here so they work across the whole supported version range.
 */
class LettuceEndpointInstrumentation implements TypeInstrumentation {

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

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object endpoint, @Advice.Argument(0) RedisCommand<?, ?, ?> command) {
      AsyncCommand<?, ?, ?> asyncCommand = asAsyncCommand(command);

      if (LettuceBatchContext.isCollecting(endpoint)) {
        LettuceBatchContext.capture(endpoint, command, asyncCommand);
        return;
      }

      // Reactive commands are not backed by an AsyncCommand future and are traced by
      // LettuceReactiveCommandsInstrumentation; only async/sync commands (backed by an AsyncCommand)
      // get their span created here, to avoid a duplicate span for reactive commands.
      if (asyncCommand == null) {
        return;
      }

      // parent context captured when the AsyncCommand was constructed during dispatch
      Context parentContext = CONTEXT.get(asyncCommand);
      if (parentContext == null) {
        parentContext = currentContext();
      }
      if (!instrumenter().shouldStart(parentContext, command)) {
        return;
      }
      Context context = instrumenter().start(parentContext, command);
      if (expectsResponse(command)) {
        asyncCommand.handleAsync(new EndCommandAsyncBiFunction<>(context, command));
      } else {
        instrumenter().end(context, command, null, null);
      }
    }

    @Nullable
    public static AsyncCommand<?, ?, ?> asAsyncCommand(RedisCommand<?, ?, ?> command) {
      // AsyncCommand itself is a CommandWrapper, so CommandWrapper.unwrap would strip past it to
      // the inner command. Walk the wrapper chain instead and stop at the AsyncCommand layer.
      RedisCommand<?, ?, ?> current = command;
      while (current != null) {
        if (current instanceof AsyncCommand) {
          return (AsyncCommand<?, ?, ?>) current;
        }
        if (current instanceof CommandWrapper) {
          current = ((CommandWrapper<?, ?, ?>) current).getDelegate();
        } else {
          break;
        }
      }
      return null;
    }
  }

  @SuppressWarnings("unused")
  public static class SetAutoFlushAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Object endpoint, @Advice.Argument(0) boolean autoFlush) {
      LettuceBatchContext.setCollecting(endpoint, !autoFlush);
    }
  }

  @SuppressWarnings("unused")
  public static class FlushAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static LettuceBatchContext.BatchScope onEnter(@Advice.This Object endpoint) {
      return LettuceBatchContext.start(endpoint);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable LettuceBatchContext.BatchScope batchScope) {
      if (throwable != null && batchScope != null) {
        batchScope.endOne(throwable);
      }
    }
  }
}
