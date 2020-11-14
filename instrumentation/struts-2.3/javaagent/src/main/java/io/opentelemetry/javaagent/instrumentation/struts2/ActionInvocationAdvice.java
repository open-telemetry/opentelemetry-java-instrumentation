/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import static io.opentelemetry.javaagent.instrumentation.struts2.Struts2Tracer.TRACER;

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
    span = TRACER.startSpan(actionInvocation);
    scope = TRACER.startScope(span);

    TRACER.updateServerSpanName(Java8BytecodeBridge.currentContext(), actionInvocation.getProxy());
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
      TRACER.endExceptionally(span, throwable);
    } else {
      TRACER.end(span);
    }
  }
}
