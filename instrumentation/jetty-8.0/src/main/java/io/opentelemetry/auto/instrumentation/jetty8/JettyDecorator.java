package io.opentelemetry.auto.instrumentation.jetty8;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JettyDecorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletResponse> {
  public static final JettyDecorator DECORATE = new JettyDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.jetty-8.0");

  @Override
  protected String getComponentName() {
    return "jetty-handler";
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
  public Span onRequest(final Span span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      final String sc = request.getContextPath();
      if (sc != null && !sc.isEmpty()) {
        span.setAttribute("servlet.context", sc);
      }
      final String sp = request.getServletPath();
      if (sp != null && !sp.isEmpty()) {
        span.setAttribute("servlet.path", sp);
      }
    }
    return super.onRequest(span, request);
  }
}
