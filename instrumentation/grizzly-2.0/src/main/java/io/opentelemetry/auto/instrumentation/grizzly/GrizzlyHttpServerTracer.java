package io.opentelemetry.auto.instrumentation.grizzly;

import static io.opentelemetry.auto.instrumentation.grizzly.GrizzlyRequestExtractAdapter.GETTER;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

public class GrizzlyHttpServerTracer extends HttpServerTracer<Request, Response> {
  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grizzly-2.0";
  }

  @Override
  protected Integer peerPort(Request request) {
    return request.getRemotePort();
  }

  @Override
  protected String peerHostIP(Request request) {
    return request.getRemoteAddr();
  }

  @Override
  protected void persistSpanToRequest(Span span, Request request) {
    request.setAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  protected URI url(Request request) throws URISyntaxException {
    return new URI(
        request.getScheme(),
        null,
        request.getServerName(),
        request.getServerPort(),
        request.getRequestURI(),
        request.getQueryString(),
        null);
  }

  @Override
  protected String method(Request request) {
    return request.getMethod().getMethodString();
  }

  @Override
  protected Getter<Request> getGetter() {
    return GETTER;
  }

  @Override
  protected int status(Response response) {
    return response.getStatus();
  }

  @Override
  protected Span findExistingSpan(Request request) {
    Object span = request.getAttribute(SPAN_ATTRIBUTE);
    return span instanceof Span ? (Span) span : null;
  }

  public void onRequest(Span span, Request request) {
    request.setAttribute("traceId", span.getContext().getTraceId().toLowerBase16());
    request.setAttribute("spanId", span.getContext().getSpanId().toLowerBase16());
    super.onRequest(span, request);
  }
}
