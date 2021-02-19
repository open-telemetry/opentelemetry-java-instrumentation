/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.instrumentation.api.context.ContextPropagationDebug;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all instrumentation specific tracer implementations.
 *
 * <p>Tracers should not use {@link Span} directly in their public APIs: ideally all lifecycle
 * methods (ex. start/end methods) should return/accept {@link Context}.
 *
 * <p>The {@link BaseTracer} offers several {@code startSpan()} utility methods for creating bare
 * spans without any attributes. If you want to provide some additional attributes on span start
 * please consider writing your own specific {@code startSpan()} method in the your tracer.
 *
 * <p>When constructing {@link Span}s tracers should set all attributes available during
 * construction on a {@link SpanBuilder} instead of a {@link Span}. This way {@code SpanProcessor}s
 * are able to see those attributes in the {@code onStart()} method and can freely read/modify them.
 */
public abstract class BaseTracer {
  private static final Meter meterProvider =
      GlobalMetricsProvider.getMeter("io.opentelemetry.instrumentation.api.tracer");

  private static final Logger log = LoggerFactory.getLogger(BaseTracer.class);

  // Keeps track of the server span for the current trace.
  // TODO(anuraaga): Should probably be renamed to local root key since it could be a consumer span
  // or other non-server root.
  private static final ContextKey<Span> CONTEXT_SERVER_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-server-span-key");

  // Keeps track of the client span in a subtree corresponding to a client request.
  private static final ContextKey<Span> CONTEXT_CLIENT_SPAN_KEY =
      ContextKey.named("opentelemetry-trace-auto-client-span-key");

  protected final Tracer tracer;
  protected final ContextPropagators propagators;
  private final LongCounter suppressionCounter =
      meterProvider
          .longCounterBuilder("agent.suppressed.spans")
          .setDescription("The number of spans that have been suppressed by the instrumentation.")
          .setUnit("1")
          .build();

  public BaseTracer() {
    tracer = GlobalOpenTelemetry.getTracer(getInstrumentationName(), getVersion());
    propagators = GlobalOpenTelemetry.getPropagators();
  }

  /**
   * Prefer to pass in an OpenTelemetry instance, rather than just a Tracer, so you don't have to
   * use the GlobalOpenTelemetry Propagator instance.
   *
   * @deprecated prefer to pass in an OpenTelemetry instance, instead.
   */
  @Deprecated
  public BaseTracer(Tracer tracer) {
    this.tracer = tracer;
    this.propagators = GlobalOpenTelemetry.getPropagators();
  }

  public BaseTracer(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer(getInstrumentationName(), getVersion());
    this.propagators = openTelemetry.getPropagators();
  }

  public ContextPropagators getPropagators() {
    return propagators;
  }

  public Context startSpan(Class<?> clazz) {
    return startSpan(spanNameForClass(clazz));
  }

  public Context startSpan(Method method) {
    return startSpan(spanNameForMethod(method));
  }

  public Context startSpan(String spanName) {
    return startSpan(spanName, SpanKind.INTERNAL);
  }

  public Context startSpan(String spanName, SpanKind kind) {
    return startSpan(Context.current(), spanName, kind);
  }

  public Context startSpan(Context parentContext, String spanName, SpanKind kind) {
    Span span = spanBuilder(spanName, kind).setParent(parentContext).startSpan();
    return parentContext.with(span);
  }

  protected SpanBuilder spanBuilder(String spanName, SpanKind kind) {
    return tracer.spanBuilder(spanName).setSpanKind(kind);
  }

  protected final Context withClientSpan(Context parentContext, Span span) {
    return parentContext.with(span).with(CONTEXT_CLIENT_SPAN_KEY, span);
  }

  protected final Context withServerSpan(Context parentContext, Span span) {
    return parentContext.with(span).with(CONTEXT_SERVER_SPAN_KEY, span);
  }

  protected final boolean shouldStartSpan(SpanKind proposedKind, Context context) {
    boolean suppressed = false;
    switch (proposedKind) {
      case CLIENT:
        suppressed = inClientSpan(context);
        break;
      case SERVER:
        suppressed = inServerSpan(context);
        break;
      default:
        break;
    }
    if (suppressed) {
      suppressionCounter.add(
          1,
          // note: an optimization here could be to make sure to re-use the labels,
          //  since the set of possible labels will be quite small in a given application.
          //  We could consider lazily creating bound counters for each combination of label values.
          Labels.of(
              "span.kind", proposedKind.name(),
              "instrumentation.name", getInstrumentationName(),
              "instrumentation.version", getVersion()));
    }
    return !suppressed;
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

  public void end(Context context) {
    end(context, -1);
  }

  public void end(Context context, long endTimeNanos) {
    end(Span.fromContext(context), endTimeNanos);
  }

  /**
   * End span.
   *
   * @deprecated Use {@link #end(Context)} instead.
   */
  @Deprecated
  public void end(Span span) {
    end(span, -1);
  }

  /**
   * End span.
   *
   * @deprecated Use {@link #end(Context, long)} instead.
   */
  @Deprecated
  public void end(Span span, long endTimeNanos) {
    if (endTimeNanos > 0) {
      span.end(endTimeNanos, TimeUnit.NANOSECONDS);
    } else {
      span.end();
    }
  }

  public void endExceptionally(Context context, Throwable throwable) {
    endExceptionally(context, throwable, -1);
  }

  public void endExceptionally(Context context, Throwable throwable, long endTimeNanos) {
    endExceptionally(Span.fromContext(context), throwable, endTimeNanos);
  }

  /**
   * End span.
   *
   * @deprecated Use {@link #endExceptionally(Context, Throwable)} instead.
   */
  @Deprecated
  public void endExceptionally(Span span, Throwable throwable) {
    endExceptionally(span, throwable, -1);
  }

  /**
   * End span.
   *
   * @deprecated Use {@link #endExceptionally(Context, Throwable, long)} instead.
   */
  @Deprecated
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

  /**
   * Do extraction with the propagators from the GlobalOpenTelemetry instance. Not recommended.
   *
   * @deprecated We should eliminate all static usages so we can use the non-global propagators.
   */
  @Deprecated
  public static <C> Context extractWithGlobalPropagators(C carrier, TextMapGetter<C> getter) {
    return extract(GlobalOpenTelemetry.getPropagators(), carrier, getter);
  }

  public <C> Context extract(C carrier, TextMapGetter<C> getter) {
    return extract(propagators, carrier, getter);
  }

  private static <C> Context extract(
      ContextPropagators propagators, C carrier, TextMapGetter<C> getter) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    // Using Context.ROOT here may be quite unexpected, but the reason is simple.
    // We want either span context extracted from the carrier or invalid one.
    // We DO NOT want any span context potentially lingering in the current context.
    return propagators.getTextMapPropagator().extract(Context.root(), carrier, getter);
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
