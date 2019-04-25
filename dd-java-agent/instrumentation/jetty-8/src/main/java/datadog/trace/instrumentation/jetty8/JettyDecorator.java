package datadog.trace.instrumentation.jetty8;

import datadog.trace.agent.decorator.HttpServerDecorator;
import io.opentracing.Span;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JettyDecorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletResponse> {
  public static final JettyDecorator DECORATE = new JettyDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jetty", "jetty-8"};
  }

  @Override
  protected String component() {
    return "jetty-handler";
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
    return httpServletRequest.getRemotePort();
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
