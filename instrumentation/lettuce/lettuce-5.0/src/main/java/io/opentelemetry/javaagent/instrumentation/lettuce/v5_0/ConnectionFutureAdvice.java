/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0;

import static io.opentelemetry.instrumentation.auto.lettuce.v5_0.LettuceConnectionDatabaseClientTracer.TRACER;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(1) RedisURI redisURI,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = TRACER.startSpan(redisURI, "CONNECT");
    scope = TRACER.startScope(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Return ConnectionFuture<?> connectionFuture,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable);
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
  }
}
