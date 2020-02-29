package io.opentelemetry.auto.typed.span;

import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

public abstract class HttpServerTypedSpan<T extends HttpServerTypedSpan, REQUEST, RESPONSE>
    extends ServerTypedSpan<T, REQUEST, RESPONSE> {

  public HttpServerTypedSpan(Tracer tracer, Span delegate) {
    super(tracer, delegate);
  }

  public T onRequest(REQUEST request) {
    return self();
  }
}
