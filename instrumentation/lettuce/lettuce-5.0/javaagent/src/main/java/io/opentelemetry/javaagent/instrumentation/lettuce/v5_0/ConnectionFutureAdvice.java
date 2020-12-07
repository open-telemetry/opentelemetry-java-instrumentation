/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceConnectionDatabaseClientTracer.tracer;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(1) RedisURI redisUri,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    context = tracer().startSpan(currentContext(), redisUri, "CONNECT");
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Return ConnectionFuture<?> connectionFuture,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();

    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(context));
  }
}
