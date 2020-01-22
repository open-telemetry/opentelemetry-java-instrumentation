package io.opentelemetry.auto.instrumentation.hibernate;

import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.Closeable;

public class CloseableSpanScopePair extends SpanScopePair implements Closeable {
  private final boolean endSpan;

  public CloseableSpanScopePair(final Span span, final Scope scope, final boolean endSpan) {
    super(span, scope);
    this.endSpan = endSpan;
  }

  @Override
  public void close() {
    if (endSpan) {
      getSpan().end();
    }  q
    getScope().close();
  }
}
