/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.api.OpenTelemetry.getGlobalPropagators;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
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

public abstract class BaseInstrumenter {
  private static final Logger log = LoggerFactory.getLogger(HttpServerInstrumenter.class);

  private static final boolean FAIL_ON_CONTEXT_LEAK =
      Boolean.getBoolean("otel.internal.failOnContextLeak");

  protected final io.opentelemetry.instrumentation.api.tracer.Tracer tracer;
  protected final Meter meter;

  public BaseInstrumenter() {
    tracer =
        new io.opentelemetry.instrumentation.api.tracer.Tracer(
            OpenTelemetry.getGlobalTracer(getInstrumentationName(), getVersion()));
    meter = OpenTelemetry.getGlobalMeter(getInstrumentationName(), getVersion());
  }

  public BaseInstrumenter(Tracer tracer) {
    this.tracer = new io.opentelemetry.instrumentation.api.tracer.Tracer(tracer);
    this.meter = OpenTelemetry.getGlobalMeter(getInstrumentationName(), getVersion());
  }

  public BaseInstrumenter(Tracer tracer, Meter meter) {
    this.tracer = new io.opentelemetry.instrumentation.api.tracer.Tracer(tracer);
    this.meter = meter;
  }

  public Context startOperation(Class<?> clazz) {
    String spanName = io.opentelemetry.instrumentation.api.tracer.Tracer.spanNameForClass(clazz);
    return startOperation(spanName, Kind.INTERNAL);
  }

  public Context startOperation(Method method) {
    String spanName = io.opentelemetry.instrumentation.api.tracer.Tracer.spanNameForMethod(method);
    return startOperation(spanName, Kind.INTERNAL);
  }

  public Context startOperation(String spanName, Kind kind) {
    Span span = tracer.startSpan(spanName, kind);
    return Context.current().with(span);
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
    return getGlobalPropagators().getTextMapPropagator().extract(Context.root(), carrier, getter);
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
}
