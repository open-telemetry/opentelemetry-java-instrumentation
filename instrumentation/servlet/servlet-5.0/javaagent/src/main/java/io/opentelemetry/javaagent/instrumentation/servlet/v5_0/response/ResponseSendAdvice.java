/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.responseInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.HttpServletResponseAdviceHelper;
import jakarta.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class ResponseSendAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(
      @Advice.This Object response,
      @Advice.Origin("#t") Class<?> declaringClass,
      @Advice.Origin("#m") String methodName,
      @Advice.Local("otelMethod") ClassAndMethod classAndMethod,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {
    callDepth = CallDepth.forClass(HttpServletResponse.class);
    if (callDepth.getAndIncrement() > 0) {
      return;
    }

    HttpServletResponseAdviceHelper.StartResult result =
        HttpServletResponseAdviceHelper.startSpan(
            responseInstrumenter(), declaringClass, methodName);
    if (result != null) {
      classAndMethod = result.getClassAndMethod();
      context = result.getContext();
      scope = result.getScope();
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelMethod") ClassAndMethod classAndMethod,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {
    if (callDepth.decrementAndGet() > 0) {
      return;
    }

    HttpServletResponseAdviceHelper.stopSpan(
        responseInstrumenter(), throwable, context, scope, classAndMethod);
  }
}
