/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response.ResponseSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
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

    Context parentContext = Java8BytecodeBridge.currentContext();
    // Don't want to generate a new top-level span
    if (Java8BytecodeBridge.spanFromContext(parentContext).getSpanContext().isValid()) {
      classAndMethod = ClassAndMethod.create(declaringClass, methodName);
      if (instrumenter().shouldStart(parentContext, classAndMethod)) {
        context = instrumenter().start(parentContext, classAndMethod);
        scope = context.makeCurrent();
      }
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
        instrumenter(), throwable, context, scope, classAndMethod);
  }
}
