/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.ratpack;

import static io.opentelemetry.instrumentation.auto.ratpack.RatpackTracer.TRACER;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import ratpack.handling.Context;

public class ErrorHandlerAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void captureThrowable(
      @Advice.Argument(0) Context ctx, @Advice.Argument(1) Throwable throwable) {
    Optional<Span> span = ctx.maybeGet(Span.class);
    if (span.isPresent()) {
      // TODO this emulates old behaviour of BaseDecorator. Has to review
      span.get().setStatus(Status.ERROR);
      TRACER.addThrowable(span.get(), throwable);
    }
  }
}
