package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.instrumentation.jaxrs.JaxRsClientDecorator.DECORATE;

import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
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
    final Span span =
        GlobalTracer.get()
            .buildSpan("jax-rs.client.call")
            .withTag(DDTags.RESOURCE_NAME, requestContext.getMethod() + " jax-rs.client.call")
            .start();
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, requestContext);

      log.debug("{} - client span started", span);

      GlobalTracer.get()
          .inject(
              span.context(),
              Format.Builtin.HTTP_HEADERS,
              new InjectAdapter(requestContext.getHeaders()));

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
      span.finish();
      log.debug("{} - client spanObj finished", spanObj);
    }
  }
}
