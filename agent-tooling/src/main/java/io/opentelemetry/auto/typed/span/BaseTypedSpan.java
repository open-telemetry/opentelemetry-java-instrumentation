package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;

public abstract class BaseTypedSpan extends DelegatingSpan {

  public BaseTypedSpan(Span delegate) {
    super(delegate);
  }

  public void end(Throwable throwable) {
    // add error details to the span.
    super.end();
  }
}
