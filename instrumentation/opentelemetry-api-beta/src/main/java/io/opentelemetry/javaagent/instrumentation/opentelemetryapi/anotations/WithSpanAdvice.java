/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.anotations;

import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.anotations.TraceAnnotationTracer.TRACER;

import application.io.opentelemetry.extensions.auto.annotations.WithSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

/**
 * Instrumentation for methods annotated with {@link WithSpan} annotation.
 *
 * @see WithSpanAnnotationInstrumentation
 */
public class WithSpanAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin Method method,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    WithSpan applicationAnnotation = method.getAnnotation(WithSpan.class);

    span =
        TRACER.startSpan(
            TRACER.spanNameForMethodWithAnnotation(applicationAnnotation, method),
            TRACER.extractSpanKind(applicationAnnotation));
    scope = span.makeCurrent();
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
