/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.context.ContextPropagationDebug;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Base class for all instrumentation specific tracer implementations.
 *
 * <p>Tracers should not use {@link Span} directly in their public APIs: ideally all lifecycle
 * methods (ex. start/end methods) should return/accept {@link Context}. By convention, {@link
 * Context} should be passed to all methods as the first parameter.
 *
 * <p>The {@link BaseTracer} offers several {@code startSpan()} utility methods for creating bare
 * spans without any attributes. If you want to provide some additional attributes on span start
 * please consider writing your own specific {@code startSpan()} method in your tracer.
 *
 * <p>A {@link Context} returned by any {@code startSpan()} method will <b>always</b> contain a new
 * span. If there is a need to suppress span creation {@link #shouldStartSpan(Context, SpanKind)}
 * should be called before {@code startSpan()}.
 *
 * <p>When constructing {@link Span}s tracers should set all attributes available during
 * construction on a {@link SpanBuilder} instead of a {@link Span}. This way {@code SpanProcessor}s
 * are able to see those attributes in the {@code onStart()} method and can freely read/modify them.
 */
public abstract class BaseTracer {
  // should we make this injectable?
  private static final SupportabilityMetrics supportability =
      new SupportabilityMetrics(Config.get()).start();

  protected final Tracer tracer;
  protected final ContextPropagators propagators;

  public BaseTracer() {
    this(GlobalOpenTelemetry.get());
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

  /**
   * The name of the instrumentation library, not the name of the instrument*ed* library. The value
   * returned by this method should uniquely identify the instrumentation library so that during
   * troubleshooting it's possible to pinpoint what tracer produced problematic telemetry.
   *
   * <p>In this project we use a convention to encode the version of the instrument*ed* library into
   * the instrumentation name, for example {@code io.opentelemetry.javaagent.apache-httpclient-4.0}.
   * This way, if there are different instrumentations for different library versions it's easy to
   * find out which instrumentations produced the telemetry data.
   *
   * @see io.opentelemetry.api.trace.TracerProvider#get(String, String)
   */
  protected abstract String getInstrumentationName();

  /**
   * The version of the instrumentation library - defaults to the value of JAR manifest attribute
   * {@code Implementation-Version}.
   */
  protected String getVersion() {
    return InstrumentationVersion.VERSION;
  }

  /**
   * Returns true if a new span of the {@code proposedKind} should be suppressed.
   *
   * <p>If the passed {@code context} contains a {@link SpanKind#SERVER} span the instrumentation
   * must not create another {@code SERVER} span. The same is true for a {@link SpanKind#CLIENT}
   * span: if one {@code CLIENT} span is already present in the passed {@code context} then another
   * one must not be started.
   *
   * @see #withClientSpan(Context, Span)
   * @see #withServerSpan(Context, Span)
   */
  public final boolean shouldStartSpan(Context context, SpanKind proposedKind) {
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
      supportability.recordSuppressedSpan(proposedKind, getInstrumentationName());
    }
    return !suppressed;
  }

  private boolean inClientSpan(Context context) {
    return ClientSpan.fromContextOrNull(context) != null;
  }

  private boolean inServerSpan(Context context) {
    return ServerSpan.fromContextOrNull(context) != null;
  }

  /**
   * Returns a {@link Context} inheriting from {@code Context.current()} that contains a new span
   * with name {@code spanName} and kind {@link SpanKind#INTERNAL}.
   */
  public Context startSpan(String spanName) {
    return startSpan(spanName, SpanKind.INTERNAL);
  }

  /**
   * Returns a {@link Context} inheriting from {@code Context.current()} that contains a new span
   * with name {@code spanName} and kind {@code kind}.
   */
  public Context startSpan(String spanName, SpanKind kind) {
    return startSpan(Context.current(), spanName, kind);
  }

  /**
   * Returns a {@link Context} inheriting from {@code parentContext} that contains a new span with
   * name {@code spanName} and kind {@code kind}.
   */
  public Context startSpan(Context parentContext, String spanName, SpanKind kind) {
    Span span = spanBuilder(spanName, kind).setParent(parentContext).startSpan();
    return parentContext.with(span);
  }

  protected SpanBuilder spanBuilder(String spanName, SpanKind kind) {
    return tracer.spanBuilder(spanName).setSpanKind(kind);
  }

  /**
   * Returns a {@link Context} containing the passed {@code span} marked as the current {@link
   * SpanKind#CLIENT} span.
   *
   * @see #shouldStartSpan(Context, SpanKind)
   */
  protected final Context withClientSpan(Context parentContext, Span span) {
    return ClientSpan.with(parentContext.with(span), span);
  }

  /**
   * Returns a {@link Context} containing the passed {@code span} marked as the current {@link
   * SpanKind#SERVER} span.
   *
   * @see #shouldStartSpan(Context, SpanKind)
   */
  protected final Context withServerSpan(Context parentContext, Span span) {
    return ServerSpan.with(parentContext.with(span), span);
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForMethod(Method method) {
    return spanNameForMethod(method.getDeclaringClass(), method.getName());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
  public static String spanNameForMethod(Class<?> clazz, @Nullable Method method) {
    return spanNameForMethod(clazz, method == null ? "<unknown>" : method.getName());
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. Anonymous classes are named based on their parent.
   */
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

  /** Ends the execution of a span stored in the passed {@code context}. */
  public void end(Context context) {
    end(context, -1);
  }

  /**
   * Ends the execution of a span stored in the passed {@code context}.
   *
   * @param endTimeNanos Explicit nanoseconds timestamp from the epoch.
   */
  public void end(Context context, long endTimeNanos) {
    Span span = Span.fromContext(context);
    if (endTimeNanos > 0) {
      span.end(endTimeNanos, TimeUnit.NANOSECONDS);
    } else {
      span.end();
    }
  }

  /**
   * Records the {@code throwable} in the span stored in the passed {@code context} and marks the
   * end of the span's execution.
   */
  public void endExceptionally(Context context, Throwable throwable) {
    endExceptionally(context, throwable, -1);
  }

  /**
   * Records the {@code throwable} in the span stored in the passed {@code context} and marks the
   * end of the span's execution.
   *
   * @param endTimeNanos Explicit nanoseconds timestamp from the epoch.
   */
  public void endExceptionally(Context context, Throwable throwable, long endTimeNanos) {
    Span span = Span.fromContext(context);
    span.setStatus(StatusCode.ERROR);
    onError(span, unwrapThrowable(throwable));
    end(context, endTimeNanos);
  }

  protected void onError(Span span, Throwable throwable) {
    addThrowable(span, throwable);
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    return throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  // TODO: call onError instead and make this private
  public void addThrowable(Span span, Throwable throwable) {
    span.recordException(throwable);
  }

  /**
   * Extracts a {@link Context} from {@code carrier} using the propagator embedded in this tracer.
   * This method can be used to propagate {@link Context} passed from upstream services.
   *
   * @see TextMapPropagator#extract(Context, Object, TextMapGetter)
   */
  public <C> Context extract(C carrier, TextMapGetter<C> getter) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    // Using Context.root() here may be quite unexpected, but the reason is simple.
    // We want either span context extracted from the carrier or invalid one.
    // We DO NOT want any span context potentially lingering in the current context.
    return propagators.getTextMapPropagator().extract(Context.root(), carrier, getter);
  }

  /**
   * Injects {@code context} data into {@code carrier} using the propagator embedded in this tracer.
   * This method can be used to propagate passed {@code context} to downstream services.
   *
   * @see TextMapPropagator#inject(Context, Object, TextMapSetter)
   */
  public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
    propagators.getTextMapPropagator().inject(context, carrier, setter);
  }
}
