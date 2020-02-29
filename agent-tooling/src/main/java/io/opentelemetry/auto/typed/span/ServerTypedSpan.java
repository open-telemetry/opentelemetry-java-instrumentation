package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;

public abstract class ServerTypedSpan<T extends ServerTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan {

  public ServerTypedSpan(Span delegate) {
    super(delegate);
  }

  public abstract T onRequest(REQUEST request);

  public abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
