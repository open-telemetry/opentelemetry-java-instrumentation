/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LettuceAsyncCommandsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.AbstractRedisAsyncCommands");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("dispatch"))
            .and(takesArgument(0, named("io.lettuce.core.protocol.RedisCommand"))),
        LettuceAsyncCommandsInstrumentation.class.getName() + "$DispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) RedisCommand<?, ?, ?> command,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();
      context = instrumenter().start(parentContext, command);
      // remember the context that called dispatch, it is used in LettuceAsyncCommandInstrumentation
      context = context.with(LettuceSingletons.COMMAND_CONTEXT_KEY, parentContext);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) RedisCommand<?, ?, ?> command,
        @Advice.Thrown Throwable throwable,
        @Advice.Return AsyncCommand<?, ?, ?> asyncCommand,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();

      if (throwable != null) {
        instrumenter().end(context, command, null, throwable);
        return;
      }

      // close spans on error or normal completion
      if (expectsResponse(command)) {
        asyncCommand.handleAsync(new EndCommandAsyncBiFunction<>(context, command));
      } else {
        instrumenter().end(context, command, null, null);
      }
    }
  }
}
