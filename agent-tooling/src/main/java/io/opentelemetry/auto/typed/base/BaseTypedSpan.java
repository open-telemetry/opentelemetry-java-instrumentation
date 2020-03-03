package io.opentelemetry.auto.typed.base;

import io.opentelemetry.trace.Span;

public abstract class BaseTypedSpan<T extends BaseTypedSpan> extends DelegatingSpan {

  public BaseTypedSpan(Span delegate) {
    super(delegate);
  }

  public void end(Throwable throwable) {
    // add error details to the span.
    super.end();
  }

  protected abstract T self();
}
