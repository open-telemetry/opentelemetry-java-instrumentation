package io.opentelemetry.auto.typed.server;

import io.opentelemetry.auto.typed.base.BaseTypedSpan;
import io.opentelemetry.trace.Span;

public abstract class ServerTypedSpan<T extends ServerTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan<T> {

  public ServerTypedSpan(Span delegate) {
    super(delegate);
  }

  protected abstract T onRequest(REQUEST request);

  protected abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
