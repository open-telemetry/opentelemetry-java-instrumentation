package datadog.trace.instrumentation.servlet3;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletResponse> {
  public static final Servlet3Decorator DECORATE = new Servlet3Decorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-3"};
  }

  @Override
  protected String component() {
    return "java-web-servlet";
  }

  @Override
  protected String method(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected URI url(final HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  @Override
  protected String peerHostIP(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemotePort();
  }

  @Override
  protected Integer status(final HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  public AgentSpan onRequest(final AgentSpan span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      span.setTag("servlet.path", request.getServletPath());
      span.setTag("servlet.context", request.getContextPath());
      onContext(span, request, request.getServletContext());
    }
    return super.onRequest(span, request);
  }

  /**
   * This method executes the filter created by
   * datadog.trace.instrumentation.springweb.DispatcherServletInstrumentation$HandlerMappingAdvice.
   * This was easier and less "hacky" than other ways to add the filter to the front of the filter
   * chain.
   */
  private void onContext(
      final AgentSpan span, final HttpServletRequest request, final ServletContext context) {
    final Object attribute = context.getAttribute("dd.dispatcher-filter");
    if (attribute instanceof Filter) {
      final Filter filter = (Filter) attribute;
      try {
        request.setAttribute(DD_SPAN_ATTRIBUTE, span);
        filter.doFilter(request, null, null);
      } catch (final IOException | ServletException e) {
      }
    }
  }

  @Override
  public AgentSpan onError(final AgentSpan span, final Throwable throwable) {
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      super.onError(span, throwable.getCause());
    } else {
      super.onError(span, throwable);
    }
    return span;
  }
}
