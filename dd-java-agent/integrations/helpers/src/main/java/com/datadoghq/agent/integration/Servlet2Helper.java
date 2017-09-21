package com.datadoghq.agent.integration;

import io.opentracing.ActiveSpan;
import io.opentracing.NoopTracerFactory;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.rule.Rule;

/** Please be very careful not to introduce any Servlet 3 dependencies into this class. */
@Slf4j
public class Servlet2Helper extends OpenTracingHelper {

  /**
   * Used as a key of {@link HttpServletRequest#setAttribute(String, Object)} to inject server span
   * context
   */
  public static final String SERVER_SPAN_CONTEXT =
      Servlet2Helper.class.getName() + ".activeSpanContext";

  public static final String SERVLET_OPERATION_NAME = "servlet.request";

  protected final Tracer tracer;

  public Servlet2Helper(final Rule rule) {
    super(rule);
    Tracer tracerResolved;
    try {
      tracerResolved = getTracer();
      tracerResolved = tracerResolved == null ? NoopTracerFactory.create() : tracerResolved;
    } catch (final Exception e) {
      tracerResolved = NoopTracerFactory.create();
      log.warn("Failed to retrieve the tracer, using a NoopTracer instead: {}", e.getMessage());
      log.warn(e.getMessage(), e);
    }
    tracer = tracerResolved;
  }

  public void onRequest(final HttpServletRequest req, final HttpServletResponse resp) {
    if (req.getAttribute(SERVER_SPAN_CONTEXT) != null) {
      // Perhaps we're already tracing?
      return;
    }

    final SpanContext extractedContext =
        tracer.extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req));

    final ActiveSpan span =
        tracer
            .buildSpan(SERVLET_OPERATION_NAME)
            .asChildOf(extractedContext)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .startActive();

    req.setAttribute(SERVER_SPAN_CONTEXT, span.context());

    ServletFilterSpanDecorator.STANDARD_TAGS.onRequest(req, span);
  }

  public void onError(
      final HttpServletRequest req, final HttpServletResponse resp, final Throwable ex) {
    if (req.getAttribute(SERVER_SPAN_CONTEXT) == null) {
      // Doesn't look like an active span was started at the beginning
      return;
    }

    final ActiveSpan span = tracer.activeSpan();
    ServletFilterSpanDecorator.STANDARD_TAGS.onError(req, resp, ex, span);
    span.deactivate();
  }

  public void onResponse(final HttpServletRequest req, final HttpServletResponse resp) {
    if (req.getAttribute(SERVER_SPAN_CONTEXT) == null) {
      // Doesn't look like an active span was started at the beginning
      return;
    }

    final ActiveSpan span = tracer.activeSpan();
    ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(req, resp, span);
    span.deactivate();
  }
}
