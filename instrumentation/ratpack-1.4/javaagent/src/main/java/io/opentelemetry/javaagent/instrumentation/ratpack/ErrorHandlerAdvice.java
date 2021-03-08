/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackTracer.tracer;

import java.util.Optional;
import net.bytebuddy.asm.Advice;
import ratpack.handling.Context;

public class ErrorHandlerAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void captureThrowable(
      @Advice.Argument(0) Context ctx, @Advice.Argument(1) Throwable throwable) {
    Optional<io.opentelemetry.context.Context> otelContext =
        ctx.maybeGet(io.opentelemetry.context.Context.class);
    if (otelContext.isPresent()) {
      tracer().onException(otelContext.get(), throwable);
    }
  }
}
