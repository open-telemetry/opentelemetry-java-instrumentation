/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class HttpServletResponseAdviceHelper {

  public static StartResult startSpan(
      Instrumenter<ClassAndMethod, Void> instrumenter, Class<?> declaringClass, String methodName) {
    Context parentContext = Context.current();
    // Don't want to generate a new top-level span
    if (Span.fromContext(parentContext).getSpanContext().isValid()) {
      ClassAndMethod classAndMethod = ClassAndMethod.create(declaringClass, methodName);
      if (instrumenter.shouldStart(parentContext, classAndMethod)) {
        Context context = instrumenter.start(parentContext, classAndMethod);
        Scope scope = context.makeCurrent();
        return new StartResult(classAndMethod, context, scope);
      }
    }

    return null;
  }

  public static final class StartResult {
    private final ClassAndMethod classAndMethod;
    private final Context context;
    private final Scope scope;

    private StartResult(ClassAndMethod classAndMethod, Context context, Scope scope) {
      this.classAndMethod = classAndMethod;
      this.context = context;
      this.scope = scope;
    }

    public ClassAndMethod getClassAndMethod() {
      return classAndMethod;
    }

    public Context getContext() {
      return context;
    }

    public Scope getScope() {
      return scope;
    }
  }

  public static void stopSpan(
      Instrumenter<ClassAndMethod, Void> instrumenter,
      Throwable throwable,
      Context context,
      Scope scope,
      ClassAndMethod request) {
    if (scope != null) {
      scope.close();

      instrumenter.end(context, request, null, throwable);
    }
  }

  private HttpServletResponseAdviceHelper() {}
}
