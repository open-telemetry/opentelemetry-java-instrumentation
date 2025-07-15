/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.responseInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.HttpServletResponseAdviceHelper;
import jakarta.servlet.http.HttpServletResponse;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class ResponseSendAdvice {

  public static class AdviceScope {
    private final ClassAndMethod classAndMethod;
    private final CallDepth callDepth;
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

    public AdviceScope(
        CallDepth callDepth, HttpServletResponseAdviceHelper.StartResult startResult) {
      this.callDepth = callDepth;
      this.classAndMethod = startResult.getClassAndMethod();
      this.context = startResult.getContext();
      this.scope = startResult.getScope();
    }

    public void exit(@Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      HttpServletResponseAdviceHelper.stopSpan(
          responseInstrumenter(), throwable, context, scope, classAndMethod);
    }
  }

  @Nullable
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AdviceScope start(
      @Advice.This Object response,
      @Advice.Origin("#t") Class<?> declaringClass,
      @Advice.Origin("#m") String methodName) {

    CallDepth callDepth = CallDepth.forClass(HttpServletResponse.class);
    return new AdviceScope(callDepth, declaringClass, methodName);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
    adviceScope.exit(throwable);
  }
}
