package datadog.trace.instrumentation.servlet2;

import datadog.trace.agent.decorator.HttpServerDecorator;
import io.opentracing.Span;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class Servlet2Decorator extends HttpServerDecorator<HttpServletRequest, ServletResponse> {
  public static final Servlet2Decorator INSTANCE = new Servlet2Decorator();

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
