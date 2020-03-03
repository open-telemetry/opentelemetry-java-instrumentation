package io.opentelemetry.auto.typed.client;

import io.opentelemetry.auto.typed.client.http.HttpClientTypedTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;

public class SampleHttpClientTypedTracer
    extends HttpClientTypedTracer<SampleHttpClientTypedSpan, String, String> {
  @Override
  protected String getInstrumentationName() {
    return "test";
  }

  @Override
  protected String getVersion() {
    return "test";
  }

  @Override
  protected String getSpanName(String o) {
    return "test-span";
  }

  @Override
  protected HttpTextFormat.Setter<String> getSetter() {
    return new HttpTextFormat.Setter<String>() {
      @Override
      public void put(String carrier, String key, String value) {}
    };
  }

  @Override
  protected SampleHttpClientTypedSpan wrapSpan(Span span) {
    return new SampleHttpClientTypedSpan(span);
  }
}
