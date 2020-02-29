package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;

public abstract class ClientTypedSpan<T extends ClientTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan {

  public ClientTypedSpan(Span delegate) {
    super(delegate);
  }

  public abstract T onRequest(REQUEST request);

  public abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
