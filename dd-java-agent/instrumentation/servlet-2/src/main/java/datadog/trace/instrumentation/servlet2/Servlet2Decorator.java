package datadog.trace.instrumentation.servlet2;

import datadog.trace.agent.decorator.HttpServerDecorator;
import io.opentracing.Span;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class Servlet2Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, ServletResponse> {
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
    return new URI(httpServletRequest.getRequestURL().toString());
  }

  @Override
  protected String peerHostname(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteHost();
  }

  @Override
  protected String peerHostIP(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  protected Integer status(final ServletResponse httpServletResponse) {
    // HttpServletResponse doesn't have accessor for status code.
    return null;
  }

  @Override
  public Span onRequest(final Span span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      span.setTag("servlet.context", request.getContextPath());
    }
    return super.onRequest(span, request);
  }
}
