package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public abstract class ServerTypedSpan<T extends ServerTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan<T> {

  public ServerTypedSpan(Tracer tracer, Span delegate) {
    super(tracer, delegate);
  }

  public abstract T onRequest(REQUEST request);

  public abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
