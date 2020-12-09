/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.javaagent.instrumentation.methods.MethodTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class MethodAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin Method method,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span =
        io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
            tracer().startOperation(method));
    scope = span.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Thrown Throwable throwable) {
    scope.close();
    if (throwable != null) {
      tracer().endExceptionally(span, throwable);
    } else {
      tracer().end(span);
    }
  }
}
