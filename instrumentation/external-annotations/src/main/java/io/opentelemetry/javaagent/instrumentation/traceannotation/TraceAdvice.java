/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.traceannotation;

import static io.opentelemetry.javaagent.instrumentation.traceannotation.TraceAnnotationTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin Method method,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = TRACER.startSpan(method);
    scope = currentContextWith(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Thrown Throwable throwable) {
    scope.close();
    if (throwable != null) {
      TRACER.endExceptionally(span, throwable);
    } else {
      TRACER.end(span);
    }
  }
}
