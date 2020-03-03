package io.opentelemetry.auto.typed.server;

import io.opentelemetry.auto.typed.base.BaseTypedTracer;
import io.opentelemetry.trace.Span;

public abstract class ServerTypedTracer<
        T extends ServerTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends BaseTypedTracer<T, REQUEST> {

  @Override
  protected Span.Kind getSpanKind() {
    return Span.Kind.SERVER;
  }

  @Override
  protected T startSpan(REQUEST request, T span) {
    return span.onRequest(request);
  }
}
