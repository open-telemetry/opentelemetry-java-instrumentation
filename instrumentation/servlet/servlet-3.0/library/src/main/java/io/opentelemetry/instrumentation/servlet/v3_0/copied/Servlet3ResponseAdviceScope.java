/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import static io.opentelemetry.instrumentation.servlet.v3_0.copied.Servlet3Singletons.responseInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import javax.annotation.Nullable;

public class Servlet3ResponseAdviceScope {
  private final CallDepth callDepth;
  private final ClassAndMethod classAndMethod;
  private final Context context;
  private final Scope scope;

  public Servlet3ResponseAdviceScope(
      CallDepth callDepth, Class<?> declaringClass, String methodName) {
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
    if (result != null) {
      classAndMethod = result.getClassAndMethod();
      context = result.getContext();
      scope = result.getScope();
    } else {
      classAndMethod = null;
      context = null;
      scope = null;
    }
  }

  public void exit(@Nullable Throwable throwable) {
    if (callDepth.decrementAndGet() > 0) {
      return;
    }
    HttpServletResponseAdviceHelper.stopSpan(
        responseInstrumenter(), throwable, context, scope, classAndMethod);
  }
}
