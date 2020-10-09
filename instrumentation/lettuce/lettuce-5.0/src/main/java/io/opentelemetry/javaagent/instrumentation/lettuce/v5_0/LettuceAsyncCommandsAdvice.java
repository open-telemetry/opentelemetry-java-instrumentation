/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0;

import static io.opentelemetry.instrumentation.auto.lettuce.v5_0.LettuceDatabaseClientTracer.TRACER;
import static io.opentelemetry.instrumentation.auto.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) RedisCommand<?, ?, ?> command,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    span = TRACER.startSpan(null, command);
    scope = TRACER.startScope(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) RedisCommand<?, ?, ?> command,
      @Advice.Thrown Throwable throwable,
      @Advice.Return AsyncCommand<?, ?, ?> asyncCommand,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable);
      return;
    }

    // close spans on error or normal completion
    if (expectsResponse(command)) {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(span));
    } else {
      TRACER.end(span);
    }
  }
}
