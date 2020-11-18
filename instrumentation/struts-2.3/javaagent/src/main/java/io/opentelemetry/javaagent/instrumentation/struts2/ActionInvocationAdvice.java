/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import static io.opentelemetry.javaagent.instrumentation.struts2.Struts2Tracer.tracer;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;

public class ActionInvocationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This ActionInvocation actionInvocation,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = tracer().startSpan(actionInvocation);
    scope = tracer().startScope(span);

    tracer()
        .updateServerSpanName(Java8BytecodeBridge.currentContext(), actionInvocation.getProxy());
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope != null) {
      scope.close();
    }
    if (throwable != null) {
      tracer().endExceptionally(span, throwable);
    } else {
      tracer().end(span);
    }
  }
}
