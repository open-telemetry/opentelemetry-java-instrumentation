/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class Tracer {

  private final io.opentelemetry.api.trace.Tracer tracer;

  public io.opentelemetry.api.trace.Tracer getTracer() {
    return tracer;
  }

  public Tracer(io.opentelemetry.api.trace.Tracer tracer) {
    this.tracer = tracer;
  }

  // Keeps track of the server span for the current trace.
  // TODO(anuraaga): Should probably be renamed to local root key since it could be a consumer span
  // or other non-server root.
  public static final ContextKey<Span> CONTEXT_SERVER_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-server-span-key");
  // Keeps track of the client span in a subtree corresponding to a client request.
  // Visible for testing
  public static final ContextKey<Span> CONTEXT_CLIENT_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-auto-client-span-key");

  /** Returns span of type SERVER from the current context or <code>null</code> if not found. */
  public static Span getCurrentServerSpan() {
    return getCurrentServerSpan(Context.current());
  }

  /** Returns span of type SERVER from the given context or <code>null</code> if not found. */
  public static Span getCurrentServerSpan(Context context) {
    return context.get(CONTEXT_SERVER_SPAN_KEY);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForMethod(Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method the method to get the name from, nullable
   * @return the span name from the class and method
   */
  public static String spanNameForMethod(Class<?> clazz, Method method) {
    return spanNameForMethod(clazz, null == method ? null : method.getName());
  }

  public static String spanNameForMethod(Class<?> cl, String methodName) {
    return spanNameForClass(cl) + "." + methodName;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForClass(Class<?> clazz) {
    if (!clazz.isAnonymousClass()) {
      return clazz.getSimpleName();
    }
    String className = clazz.getName();
    if (clazz.getPackage() != null) {
      String pkgName = clazz.getPackage().getName();
      if (!pkgName.isEmpty()) {
        className = clazz.getName().replace(pkgName, "").substring(1);
      }
    }
    return className;
  }

  public Span getCurrentSpan() {
    return Span.current();
  }

  public Span startSpan(String spanName, Span.Kind kind) {
    return this.tracer.spanBuilder(spanName).setSpanKind(kind).startSpan();
  }

  public Span startSpan(String spanName, Span.Kind kind, Context parentContext) {
    return startSpan(spanName, kind, parentContext, -1);
  }

  public Span startSpan(
      String spanName, Span.Kind kind, Context parentContext, long startTimestamp) {
    SpanBuilder builder = tracer.spanBuilder(spanName).setSpanKind(kind).setParent(parentContext);
    if (startTimestamp >= 0) {
      builder.setStartTimestamp(startTimestamp, TimeUnit.NANOSECONDS);
    }
    return builder.startSpan();
  }

  public SpanBuilder spanBuilder(String name) {
    return tracer.spanBuilder(name);
  }
}
