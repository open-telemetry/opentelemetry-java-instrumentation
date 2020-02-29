package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public abstract class BaseTypedSpan<T extends BaseTypedSpan> extends DelegatingSpan {

  protected final Tracer tracer;

  public BaseTypedSpan(Tracer tracer, Span delegate) {
    super(delegate);
    this.tracer = tracer;
  }

  public void end(Throwable throwable) {
    // add error details to the span.
    super.end();
  }

  abstract protected T self();
}
