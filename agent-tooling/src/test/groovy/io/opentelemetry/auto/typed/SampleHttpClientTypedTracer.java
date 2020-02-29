package io.opentelemetry.auto.typed;

import io.opentelemetry.auto.typed.tracer.HttpClientTypedTracer;
import io.opentelemetry.trace.Span;

public class SampleHttpClientTypedTracer
    extends HttpClientTypedTracer<SampleHttpClientTypedSpan, Object, Object> {
  @Override
  protected String getInstrumentationName() {
    return "test";
  }

  @Override
  protected String getVersion() {
    return "test";
  }

  @Override
  protected String getSpanName(Object o) {
    return "test-span";
  }

  @Override
  protected SampleHttpClientTypedSpan wrapSpan(Span span) {
    return new SampleHttpClientTypedSpan(span);
  }
}
