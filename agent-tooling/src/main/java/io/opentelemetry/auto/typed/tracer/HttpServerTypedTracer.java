package io.opentelemetry.auto.typed.tracer;

import io.opentelemetry.auto.typed.span.HttpServerTypedSpan;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpServerTypedTracer<
        T extends HttpServerTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ServerTypedTracer<T, REQUEST, RESPONSE> {

  @Override
  protected Span.Builder buildSpan(REQUEST request, Span.Builder spanBuilder) {
    SpanContext extract = tracer.getHttpTextFormat().extract(request, getGetter());
    spanBuilder.setParent(extract);
    return super.buildSpan(request, spanBuilder);
  }

  protected abstract HttpTextFormat.Getter<REQUEST> getGetter();
}
