package io.opentelemetry.auto.typed.client.http;

import io.opentelemetry.auto.typed.client.ClientTypedSpan;
import io.opentelemetry.trace.Span;

public abstract class HttpClientTypedSpan<T extends HttpClientTypedSpan, REQUEST, RESPONSE>
    extends ClientTypedSpan<T, REQUEST, RESPONSE> {

  public HttpClientTypedSpan(Span delegate) {
    super(delegate);
  }
}
