/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceDatabaseClientTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) RedisCommand<?, ?, ?> command,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    context = tracer().startOperation(currentContext(), null, command);
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
      tracer().endExceptionally(context, throwable);
      return;
    }

    // close spans on error or normal completion
    if (expectsResponse(command)) {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(context));
    } else {
      tracer().end(context);
    }
  }
}
