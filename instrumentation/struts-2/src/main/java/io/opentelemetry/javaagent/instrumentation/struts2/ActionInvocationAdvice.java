/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

public class ActionInvocationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This ActionInvocation actionProxy,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    span = Struts2Tracer.TRACER.startSpan(actionProxy);
    scope = Struts2Tracer.TRACER.startScope(span);
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
      Struts2Tracer.TRACER.endExceptionally(span, throwable);
    } else {
      Struts2Tracer.TRACER.end(span);
    }
  }
}
