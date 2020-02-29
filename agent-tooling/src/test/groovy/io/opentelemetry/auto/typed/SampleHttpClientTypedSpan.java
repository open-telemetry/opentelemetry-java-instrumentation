package io.opentelemetry.auto.typed;

import io.opentelemetry.auto.typed.span.HttpClientTypedSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;

public class SampleHttpClientTypedSpan
    extends HttpClientTypedSpan<SampleHttpClientTypedSpan, Object, Object> {
  public SampleHttpClientTypedSpan(Span delegate) {
    super(delegate);
  }

  @Override
  public SampleHttpClientTypedSpan onRequest(Object o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  public SampleHttpClientTypedSpan onResponse(Object o) {
    delegate.setStatus(Status.OK);
    return this;
  }
}
