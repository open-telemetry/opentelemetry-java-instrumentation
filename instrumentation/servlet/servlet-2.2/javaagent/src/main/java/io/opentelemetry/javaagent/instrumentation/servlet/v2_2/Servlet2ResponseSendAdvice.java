/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2Singletons.responseInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.HttpServletResponseAdviceHelper;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Servlet2ResponseSendAdvice {

  public static class AdviceScope {
    private final CallDepth callDepth;
    private final ClassAndMethod classAndMethod;
    private final Context context;
    private final Scope scope;

    public AdviceScope(CallDepth callDepth, Class<?> declaringClass, String methodName) {
      this.callDepth = callDepth;
      if (callDepth.getAndIncrement() > 0) {
        this.classAndMethod = null;
        this.context = null;
        this.scope = null;
        return;
      }

      HttpServletResponseAdviceHelper.StartResult result =
          HttpServletResponseAdviceHelper.startSpan(
              responseInstrumenter(), declaringClass, methodName);
      if (result == null) {
        this.classAndMethod = null;
        this.context = null;
        this.scope = null;
        return;
      }
      this.classAndMethod = result.getClassAndMethod();
      this.context = result.getContext();
      this.scope = result.getScope();
    }

    public void exit(@Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      HttpServletResponseAdviceHelper.stopSpan(
          responseInstrumenter(), throwable, context, scope, classAndMethod);
    }
  }

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AdviceScope start(
      @Advice.Origin("#t") Class<?> declaringClass, @Advice.Origin("#m") String methodName) {
    return new AdviceScope(
        CallDepth.forClass(HttpServletResponse.class), declaringClass, methodName);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
    adviceScope.exit(throwable);
  }
}
