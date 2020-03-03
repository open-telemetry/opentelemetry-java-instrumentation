package io.opentelemetry.auto.typed.server.http;

import io.opentelemetry.auto.typed.server.ServerTypedSpan;
import io.opentelemetry.trace.Span;

public abstract class HttpServerTypedSpan<T extends HttpServerTypedSpan, REQUEST, RESPONSE>
    extends ServerTypedSpan<T, REQUEST, RESPONSE> {

  public HttpServerTypedSpan(Span delegate) {
    super(delegate);
  }
}
