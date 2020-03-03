package io.opentelemetry.auto.typed.client.http;

import io.opentelemetry.auto.typed.client.ClientTypedTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpClientTypedTracer<
        T extends HttpClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ClientTypedTracer<T, REQUEST, RESPONSE> {

  @Override
  protected T startSpan(REQUEST request, T span) {
    tracer.getHttpTextFormat().inject(span.getContext(), request, getSetter());
    return super.startSpan(request, span);
  }

  protected abstract HttpTextFormat.Setter<REQUEST> getSetter();
}
