package io.opentelemetry.auto.typed.server.http;

import io.opentelemetry.context.Scope;

public class HttpServerSpanWithScope {
  private final HttpServerSpan span;
  private final Scope scope;

  public HttpServerSpanWithScope(final HttpServerSpan span, final Scope scope) {
    this.span = span;
    this.scope = scope;
  }

  public HttpServerSpan getSpan() {
    return span;
  }

  public void closeScope() {
    scope.close();
  }
}
