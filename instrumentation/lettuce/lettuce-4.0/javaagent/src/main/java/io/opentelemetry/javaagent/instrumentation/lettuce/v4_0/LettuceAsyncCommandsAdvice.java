/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceDatabaseClientTracer.tracer;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) RedisCommand<?, ?, ?> command,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    context = tracer().startSpan(currentContext(), null, command);
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Argument(0) RedisCommand<?, ?, ?> command,
      @Advice.Thrown Throwable throwable,
      @Advice.Return AsyncCommand<?, ?, ?> asyncCommand,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    scope.close();
    InstrumentationPoints.afterCommand(command, context, throwable, asyncCommand);
  }
}
