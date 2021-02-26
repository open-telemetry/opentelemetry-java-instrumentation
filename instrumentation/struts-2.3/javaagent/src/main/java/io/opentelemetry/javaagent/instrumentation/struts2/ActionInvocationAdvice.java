/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts2;

import static io.opentelemetry.javaagent.instrumentation.struts2.Struts2Tracer.tracer;

import com.opensymphony.xwork2.ActionInvocation;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;

public class ActionInvocationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This ActionInvocation actionInvocation,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    Context parentContext = Java8BytecodeBridge.currentContext();

    context = tracer().startSpan(parentContext, actionInvocation);
    scope = context.makeCurrent();

    tracer().updateServerSpanName(parentContext, actionInvocation.getProxy());
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (scope != null) {
      scope.close();
    }
    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else {
      tracer().end(context);
    }
  }
}
