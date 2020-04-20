package datadog.trace.instrumentation.servlet2;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;

public class Servlet2Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletRequest> {
  public static final Servlet2Decorator DECORATE = new Servlet2Decorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-2"};
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
    // HttpServletResponse doesn't have accessor for remote port.
    return null;
  }

  @Override
  protected Integer status(final HttpServletRequest httpServletRequest) {
    if (httpServletRequest != null) {
      return (Integer) httpServletRequest.getAttribute("dd.http-status");
    } else {
      return null;
    }
  }

  @Override
  public AgentSpan onRequest(final AgentSpan span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      span.setTag("servlet.context", request.getContextPath());
      span.setTag("servlet.path", request.getServletPath());
    }
    return super.onRequest(span, request);
  }
}
