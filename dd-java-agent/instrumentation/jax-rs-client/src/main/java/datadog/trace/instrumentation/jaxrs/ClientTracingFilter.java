package datadog.trace.instrumentation.jaxrs;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
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
  private static final String PROPERTY_NAME = ClientTracingFilter.class.getName() + ".span";

  @Override
  public void filter(final ClientRequestContext requestContext) throws IOException {

    final Span span =
        GlobalTracer.get()
            .buildSpan("jax-rs.client.call")
            .withTag(Tags.COMPONENT.getKey(), "jax-rs.client")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
            .withTag(Tags.HTTP_URL.getKey(), requestContext.getUri().toString())
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
            .withTag(DDTags.RESOURCE_NAME, requestContext.getMethod() + " jax-rs.client.call")
            .start();

    log.debug("{} - client span started", span);

    GlobalTracer.get()
        .inject(
            span.context(),
            Format.Builtin.HTTP_HEADERS,
            new InjectAdapter(requestContext.getHeaders()));
    requestContext.setProperty(PROPERTY_NAME, span);
  }

  @Override
  public void filter(
      final ClientRequestContext requestContext, final ClientResponseContext responseContext)
      throws IOException {
    final Object spanObj = requestContext.getProperty(PROPERTY_NAME);
    if (spanObj instanceof Span) {
      final Span span = (Span) spanObj;
      Tags.HTTP_STATUS.set(span, responseContext.getStatus());

      span.finish();
      log.debug("{} - client spanObj finished", spanObj);
    }
  }
}
