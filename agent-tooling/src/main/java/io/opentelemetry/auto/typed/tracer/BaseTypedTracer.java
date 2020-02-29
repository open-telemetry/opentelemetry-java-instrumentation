package io.opentelemetry.auto.typed.tracer;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.typed.span.BaseTypedSpan;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public abstract class BaseTypedTracer<T extends BaseTypedSpan, INSTANCE> {

  protected final Tracer tracer;

  protected BaseTypedTracer() {
    tracer = OpenTelemetry.getTracerFactory().get(getInstrumentationName(), getVersion());
  }

  protected abstract String getInstrumentationName();

  protected abstract String getVersion();

  public final T startSpan(INSTANCE instance) {
    Span.Builder builder = tracer.spanBuilder(getSpanName(instance)).setSpanKind(getSpanKind());
    builder = buildSpan(builder);
    T wrappedSpan = wrapSpan(builder.startSpan());
    return startSpan(wrappedSpan, instance);
  }

  public final Scope withSpan(T span) {
    return tracer.withSpan(span);
  }

  protected abstract String getSpanName(INSTANCE instance);

  protected abstract Span.Kind getSpanKind();

  // Allow adding additional attributes before start.
  protected Span.Builder buildSpan(Span.Builder spanBuilder) {
    return spanBuilder;
  }

  // Wrap the started span with the type specific wrapper.
  protected abstract T wrapSpan(Span span);

  // Allow adding additional attributes after start.
  protected T startSpan(T span, INSTANCE instance) {
    return span;
  }
}
