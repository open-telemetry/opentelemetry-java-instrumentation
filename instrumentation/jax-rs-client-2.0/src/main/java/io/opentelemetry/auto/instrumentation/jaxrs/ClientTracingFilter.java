package io.opentelemetry.auto.instrumentation.jaxrs;

import static io.opentelemetry.auto.instrumentation.jaxrs.InjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.jaxrs.JaxRsClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs.JaxRsClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Priority(Priorities.HEADER_DECORATOR)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
  public static final String SPAN_PROPERTY_NAME = "io.opentelemetry.auto.jax-rs-client.span";

  @Override
  public void filter(final ClientRequestContext requestContext) {
    final Span span = TRACER.spanBuilder("jax-rs.client.call").setSpanKind(CLIENT).startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, requestContext);

      TRACER.getHttpTextFormat().inject(span.getContext(), requestContext.getHeaders(), SETTER);

      requestContext.setProperty(SPAN_PROPERTY_NAME, span);
    }
  }

  @Override
  public void filter(
      final ClientRequestContext requestContext, final ClientResponseContext responseContext) {
    final Object spanObj = requestContext.getProperty(SPAN_PROPERTY_NAME);
    if (spanObj instanceof Span) {
      final Span span = (Span) spanObj;
      DECORATE.onResponse(span, responseContext);
      DECORATE.beforeFinish(span);
      span.end();
    }
  }
}
