package io.opentelemetry.auto.typed;

import io.opentelemetry.auto.typed.span.HttpClientTypedSpan;
import io.opentelemetry.auto.typed.span.HttpServerTypedSpan;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;

public class SampleHttpServerTypedSpan
    extends HttpServerTypedSpan<SampleHttpServerTypedSpan, String, String> {
  public SampleHttpServerTypedSpan(Tracer tracer, Span delegate) {
    super(tracer, delegate);
  }

  @Override
  public SampleHttpServerTypedSpan onRequest(String o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  public SampleHttpServerTypedSpan onResponse(String o) {
    delegate.setStatus(Status.OK);
    return this;
  }

  @Override
  protected SampleHttpServerTypedSpan self() {
    return this;
  }
}
