package io.opentelemetry.auto.typed.base;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

public abstract class BaseTypedTracer<T extends BaseTypedSpan, INSTANCE> {

  protected final Tracer tracer;

  protected BaseTypedTracer() {
    tracer = OpenTelemetry.getTracerFactory().get(getInstrumentationName(), getVersion());
  }

  protected abstract String getInstrumentationName();

  protected abstract String getVersion();

  public final T startSpan(INSTANCE instance) {
    return startSpan(instance, tracer.spanBuilder(getSpanName(instance)));
  }

  public final T startSpan(INSTANCE instance, Span parent) {
    return startSpan(instance, tracer.spanBuilder(getSpanName(instance)).setParent(parent));
  }

  public final T startSpan(INSTANCE instance, SpanContext parent) {
    return startSpan(instance, tracer.spanBuilder(getSpanName(instance)).setParent(parent));
  }

  private T startSpan(INSTANCE instance, Span.Builder builder) {
    builder = buildSpan(instance, builder.setSpanKind(getSpanKind()));
    T wrappedSpan = wrapSpan(builder.startSpan());
    return startSpan(instance, wrappedSpan);
  }

  public final Scope withSpan(T span) {
    return tracer.withSpan(span);
  }

  protected abstract String getSpanName(INSTANCE instance);

  protected abstract Span.Kind getSpanKind();

  // Allow adding additional attributes before start.
  // eg spanBuilder.setNoParent() or tracer.extract.
  protected Span.Builder buildSpan(INSTANCE instance, Span.Builder spanBuilder) {
    return spanBuilder;
  }

  // Wrap the started span with the type specific wrapper.
  protected abstract T wrapSpan(Span span);

  // Allow adding additional attributes after start.
  protected T startSpan(INSTANCE instance, T span) {
    return span;
  }
}
