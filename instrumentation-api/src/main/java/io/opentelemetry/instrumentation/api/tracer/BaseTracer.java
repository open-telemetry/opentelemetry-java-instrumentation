/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class BaseTracer {
  // Keeps track of the server span for the current trace.
  // TODO(anuraaga): Should probably be renamed to local root key since it could be a consumer span
  // or other non-server root.
  public static final ContextKey<Span> CONTEXT_SERVER_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-server-span-key");

  // Keeps track of the client span in a subtree corresponding to a client request.
  // Visible for testing
  public static final ContextKey<Span> CONTEXT_CLIENT_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-auto-client-span-key");

  protected final Tracer tracer;

  public BaseTracer() {
    tracer = OpenTelemetry.getGlobalTracer(getInstrumentationName(), getVersion());
  }

  public BaseTracer(Tracer tracer) {
    this.tracer = tracer;
  }

  public Span startSpan(Class<?> clazz) {
    String spanName = spanNameForClass(clazz);
    return startSpan(spanName, Kind.INTERNAL);
  }

  public Span startSpan(Method method) {
    String spanName = spanNameForMethod(method);
    return startSpan(spanName, Kind.INTERNAL);
  }

  public Span startSpan(String spanName, Kind kind) {
    return tracer.spanBuilder(spanName).setSpanKind(kind).startSpan();
  }

  public Scope startScope(Span span) {
    return Context.current().with(span).makeCurrent();
  }

  public Span getCurrentSpan() {
    return Span.current();
  }

  protected abstract String getInstrumentationName();

  protected String getVersion() {
    return InstrumentationVersion.VERSION;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForMethod(Method method) {
    return spanNameForClass(method.getDeclaringClass()) + "." + method.getName();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   *
   * @param method the method to get the name from, nullable
   * @return the span name from the class and method
   */
  protected String spanNameForMethod(Class<?> clazz, Method method) {
    return spanNameForMethod(clazz, null == method ? null : method.getName());
  }

  protected String spanNameForMethod(Class<?> cl, String methodName) {
    return spanNameForClass(cl) + "." + methodName;
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given class
   * reference. Anonymous classes are named based on their parent.
   */
  public String spanNameForClass(Class<?> clazz) {
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

  public void end(Span span) {
    end(span, -1);
  }

  public void end(Span span, long endTimeNanos) {
    if (endTimeNanos > 0) {
      span.end(endTimeNanos, TimeUnit.NANOSECONDS);
    } else {
      span.end();
    }
  }

  public void endExceptionally(Span span, Throwable throwable) {
    endExceptionally(span, throwable, -1);
  }

  public void endExceptionally(Span span, Throwable throwable, long endTimeNanos) {
    span.setStatus(StatusCode.ERROR);
    onError(span, unwrapThrowable(throwable));
    end(span, endTimeNanos);
  }

  protected void onError(Span span, Throwable throwable) {
    addThrowable(span, throwable);
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    return throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  public void addThrowable(Span span, Throwable throwable) {
    span.recordException(throwable);
  }

  /** Returns span of type SERVER from the current context or <code>null</code> if not found. */
  public static Span getCurrentServerSpan() {
    return getCurrentServerSpan(Context.current());
  }

  /** Returns span of type SERVER from the given context or <code>null</code> if not found. */
  public static Span getCurrentServerSpan(Context context) {
    return context.get(CONTEXT_SERVER_SPAN_KEY);
  }
}
