package io.opentelemetry.auto.typed;

import io.opentelemetry.auto.typed.tracer.HttpClientTypedTracer;
import io.opentelemetry.auto.typed.tracer.HttpServerTypedTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;

import javax.annotation.Nullable;

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
    return new SampleHttpServerTypedSpan(tracer, span);
  }

  @Override
  protected HttpTextFormat.Getter<String> getGetter() {
    return new HttpTextFormat.Getter<String>() {
      @Nullable
      @Override
      public String get(String carrier, String key) {
        return null;
      }
    };
  }
}
