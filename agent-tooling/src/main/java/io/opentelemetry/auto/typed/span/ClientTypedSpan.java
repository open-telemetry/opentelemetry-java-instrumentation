package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public abstract class ClientTypedSpan<T extends ClientTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan<T> {

  public ClientTypedSpan(Tracer tracer, Span delegate) {
    super(tracer, delegate);
  }

  public abstract T onRequest(REQUEST request);

  public abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
