package io.opentelemetry.auto.typed.tracer;

import io.opentelemetry.auto.typed.span.ClientTypedSpan;
import io.opentelemetry.trace.Span;

public abstract class ClientTypedTracer<
        T extends ClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends BaseTypedTracer<T, REQUEST> {
  @Override
  protected Span.Kind getSpanKind() {
    return Span.Kind.CLIENT;
  }

  @Override
  protected T startSpan(T span, REQUEST request) {
    return span.onRequest(request);
  }
}
