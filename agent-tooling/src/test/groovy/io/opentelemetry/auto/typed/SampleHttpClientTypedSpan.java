package io.opentelemetry.auto.typed;

import io.opentelemetry.auto.typed.span.HttpClientTypedSpan;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;

public class SampleHttpClientTypedSpan
    extends HttpClientTypedSpan<SampleHttpClientTypedSpan, String, String> {
  public SampleHttpClientTypedSpan(Tracer tracer, Span delegate) {
    super(tracer, delegate);
  }

  @Override
  public SampleHttpClientTypedSpan onRequest(String o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  protected HttpTextFormat.Setter<String> getSetter() {
    return new HttpTextFormat.Setter<String>() {
      @Override
      public void put(String carrier, String key, String value) {
      }
    };
  }

  @Override
  public SampleHttpClientTypedSpan onResponse(String o) {
    delegate.setStatus(Status.OK);
    return this;
  }

  @Override
  protected SampleHttpClientTypedSpan self() {
    return this;
  }
}
