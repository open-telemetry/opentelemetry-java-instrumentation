package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jaxrs.InjectAdapter.SETTER;
import static datadog.trace.instrumentation.jaxrs.JaxRsClientDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
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
  public static final String SPAN_PROPERTY_NAME = "datadog.trace.jax-rs-client.span";

  @Override
  public void filter(final ClientRequestContext requestContext) {
    final AgentSpan span = startSpan("jax-rs.client.call");
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, requestContext);

      propagate().inject(span, requestContext.getHeaders(), SETTER);

      requestContext.setProperty(SPAN_PROPERTY_NAME, span);
    }
  }

  @Override
  public void filter(
      final ClientRequestContext requestContext, final ClientResponseContext responseContext) {
    final Object spanObj = requestContext.getProperty(SPAN_PROPERTY_NAME);
    if (spanObj instanceof AgentSpan) {
      final AgentSpan span = (AgentSpan) spanObj;
      DECORATE.onResponse(span, responseContext);
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }
}
