package datadog.trace.instrumentation.springweb;

import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.springweb.SpringWebHttpServerDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

public class HandlerMappingResourceNameFilter implements Filter {
  private volatile List<HandlerMapping> handlerMappings;

  @Override
  public void init(final FilterConfig filterConfig) {}

  @Override
  public void doFilter(
      final ServletRequest servletRequest,
      final ServletResponse servletResponse,
      final FilterChain filterChain) {
    if (servletRequest instanceof HttpServletRequest && handlerMappings != null) {
      final HttpServletRequest request = (HttpServletRequest) servletRequest;
      try {
        if (findMapping(request)) {
          // Name the parent span based on the matching pattern
          final Object parentSpan = request.getAttribute(DD_SPAN_ATTRIBUTE);
          if (parentSpan instanceof AgentSpan) {
            // Let the parent span resource name be set with the attribute set in findMapping.
            DECORATE.onRequest((AgentSpan) parentSpan, request);
          }
        }
      } catch (final Exception e) {
      }
    }
  }

  @Override
  public void destroy() {}

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute is read by
   * SpringWebHttpServerDecorator.onRequest and set as the resource name.
   */
  private boolean findMapping(final HttpServletRequest request) throws Exception {
    for (final HandlerMapping mapping : handlerMappings) {
      final HandlerExecutionChain handler = mapping.getHandler(request);
      if (handler != null) {
        return true;
      }
    }
    return false;
  }

  public void setHandlerMappings(final List<HandlerMapping> handlerMappings) {
    this.handlerMappings = handlerMappings;
  }
}
