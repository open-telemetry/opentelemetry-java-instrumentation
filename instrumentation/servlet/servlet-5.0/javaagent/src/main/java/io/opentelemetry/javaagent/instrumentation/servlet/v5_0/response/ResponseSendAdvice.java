/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response.ResponseSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.HttpServletResponseAdviceHelper;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class ResponseSendAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(
      @Advice.Origin Method method,
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
      if (instrumenter().shouldStart(parentContext, method)) {
        context = instrumenter().start(parentContext, method);
        scope = context.makeCurrent();
      }
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Origin Method method,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {
    if (callDepth.decrementAndGet() > 0) {
      return;
    }
    HttpServletResponseAdviceHelper.stopSpan(instrumenter(), throwable, context, scope, method);
  }
}
