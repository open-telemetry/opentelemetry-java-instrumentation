package io.opentelemetry.auto.typed.client;

import io.opentelemetry.auto.typed.base.BaseTypedTracer;
import io.opentelemetry.trace.Span;

public abstract class ClientTypedTracer<
        T extends ClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends BaseTypedTracer<T, REQUEST> {
  @Override
  protected Span.Kind getSpanKind() {
    return Span.Kind.CLIENT;
  }

  @Override
  protected T startSpan(REQUEST request, T span) {
    return span.onRequest(request);
  }
}
