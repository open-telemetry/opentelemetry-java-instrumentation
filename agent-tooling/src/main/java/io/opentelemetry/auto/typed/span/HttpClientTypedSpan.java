package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;

public abstract class HttpClientTypedSpan<T extends HttpClientTypedSpan, REQUEST, RESPONSE>
    extends ClientTypedSpan<T, REQUEST, RESPONSE> {

  public HttpClientTypedSpan(Span delegate) {
    super(delegate);
  }

  public abstract T onRequest(REQUEST request);

  public abstract T onResponse(RESPONSE response);
}
