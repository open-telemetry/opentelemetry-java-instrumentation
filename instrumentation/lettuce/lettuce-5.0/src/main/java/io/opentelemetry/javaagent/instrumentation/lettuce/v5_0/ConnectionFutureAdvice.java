/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceConnectionDatabaseClientTracer.tracer;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(1) RedisURI redisUri,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = tracer().startSpan(redisUri, "CONNECT");
    scope = tracer().startScope(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Return ConnectionFuture<?> connectionFuture,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();

    if (throwable != null) {
      tracer().endExceptionally(span, throwable);
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
  }
}
