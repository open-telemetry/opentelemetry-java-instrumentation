package io.opentelemetry.auto.typed.span;

import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public abstract class HttpClientTypedSpan<T extends HttpClientTypedSpan, REQUEST, RESPONSE>
    extends ClientTypedSpan<T, REQUEST, RESPONSE> {

  public HttpClientTypedSpan(Tracer tracer, Span delegate) {
    super(tracer, delegate);
  }

  public T onRequest(REQUEST request) {
    tracer.getHttpTextFormat().inject(getContext(), request, getSetter());
    return self();
  }

  protected abstract HttpTextFormat.Setter<REQUEST> getSetter();
}
