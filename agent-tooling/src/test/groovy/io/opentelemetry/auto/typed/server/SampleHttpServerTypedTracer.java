package io.opentelemetry.auto.typed.server;

import io.opentelemetry.auto.typed.server.http.HttpServerTypedTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;

public class SampleHttpServerTypedTracer
    extends HttpServerTypedTracer<SampleHttpServerTypedSpan, String, String> {
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
  protected SampleHttpServerTypedSpan wrapSpan(Span span) {
    return new SampleHttpServerTypedSpan(span);
  }

  @Override
  protected HttpTextFormat.Getter<String> getGetter() {
    return new HttpTextFormat.Getter<String>() {
      @Override
      public String get(String carrier, String key) {
        return null;
      }
    };
  }
}
