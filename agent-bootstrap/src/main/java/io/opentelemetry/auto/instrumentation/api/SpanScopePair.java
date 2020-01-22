package io.opentelemetry.auto.instrumentation.api;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;

// intentionally (for now) not implementing Scope or Closeable
public class SpanScopePair {
  private final Span span;
  private final Scope scope;

  public SpanScopePair(final Span span, final Scope scope) {
    this.span = span;
    this.scope = scope;
  }

  public Span getSpan() {
    return span;
  }

  public Scope getScope() {
    return scope;
  }
}
