package datadog.trace.instrumentation.servlet3;

import datadog.trace.agent.decorator.HttpServerDecorator;
import io.opentracing.Span;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletResponse> {
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
  protected String url(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRequestURL().toString();
  }

  @Override
  protected String hostname(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getServerName();
  }

  @Override
  protected Integer port(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getServerPort();
  }

  @Override
  protected Integer status(final HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
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
