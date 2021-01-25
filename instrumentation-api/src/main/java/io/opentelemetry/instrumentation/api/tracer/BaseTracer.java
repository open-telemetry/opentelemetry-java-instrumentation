/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.instrumentation.api.context.ContextPropagationDebug;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseTracer {
  private static final Logger log = LoggerFactory.getLogger(HttpServerTracer.class);

  private static final boolean FAIL_ON_CONTEXT_LEAK =
      Boolean.getBoolean("otel.internal.failOnContextLeak");

  // Keeps track of the server span for the current trace.
  // TODO(anuraaga): Should probably be renamed to local root key since it could be a consumer span
  // or other non-server root.
  private static final ContextKey<Span> CONTEXT_SERVER_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-server-span-key");

  // Keeps track of the client span in a subtree corresponding to a client request.
  private static final ContextKey<Span> CONTEXT_CLIENT_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-auto-client-span-key");

  protected final Tracer tracer;

  public BaseTracer() {
    tracer = GlobalOpenTelemetry.getTracer(getInstrumentationName(), getVersion());
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

  protected final Context withClientSpan(Context parentContext, Span span) {
    return parentContext.with(span).with(CONTEXT_CLIENT_SPAN_KEY, span);
  }

  protected final Context withServerSpan(Context parentContext, Span span) {
    return parentContext.with(span).with(CONTEXT_SERVER_SPAN_KEY, span);
  }

  public Scope startScope(Span span) {
    return Context.current().with(span).makeCurrent();
  }

  public Span getCurrentSpan() {
    return Span.current();
  }

  protected final boolean shouldStartSpan(Kind proposedKind, Context context) {
    switch (proposedKind) {
      case CLIENT:
        return !inClientSpan(context);
      case SERVER:
        return !inServerSpan(context);
      default:
        return true;
    }
  }

  private boolean inClientSpan(Context parentContext) {
    return parentContext.get(CONTEXT_CLIENT_SPAN_KEY) != null;
  }

  private boolean inServerSpan(Context context) {
    return getCurrentServerSpan(context) != null;
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

  public static <C> Context extract(C carrier, TextMapPropagator.Getter<C> getter) {
    if (ContextPropagationDebug.isThreadPropagationDebuggerEnabled()) {
      debugContextLeak();
    }
    // Using Context.ROOT here may be quite unexpected, but the reason is simple.
    // We want either span context extracted from the carrier or invalid one.
    // We DO NOT want any span context potentially lingering in the current context.
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.root(), carrier, getter);
  }

  private static void debugContextLeak() {
    Context current = Context.current();
    if (current != Context.root()) {
      log.error("Unexpected non-root current context found when extracting remote context!");
      Span currentSpan = Span.fromContextOrNull(current);
      if (currentSpan != null) {
        log.error("It contains this span: {}", currentSpan);
      }
      List<StackTraceElement[]> locations = ContextPropagationDebug.getLocations(current);
      if (locations != null) {
        StringBuilder sb = new StringBuilder();
        Iterator<StackTraceElement[]> i = locations.iterator();
        while (i.hasNext()) {
          for (StackTraceElement ste : i.next()) {
            sb.append("\n");
            sb.append(ste);
          }
          if (i.hasNext()) {
            sb.append("\nwhich was propagated from:");
          }
        }
        log.error("a context leak was detected. it was propagated from:{}", sb);
      }

      if (FAIL_ON_CONTEXT_LEAK) {
        throw new IllegalStateException("Context leak detected");
      }
    }
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
