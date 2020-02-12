package datadog.trace.instrumentation.finatra;

import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;

public class FinatraDecorator extends HttpServerDecorator<Request, Request, Response> {
  public static final FinatraDecorator DECORATE = new FinatraDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.finatra-2.9");

  @Override
  protected String getComponentName() {
    return "finatra";
  }

  @Override
  protected String method(final Request request) {
    return request.method().name();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return URI.create(request.uri());
  }

  @Override
  protected String peerHostname(final Request request) {
    return request.remoteHost();
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.remoteAddress().getHostAddress();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return request.remotePort();
  }

  @Override
  protected Integer status(final Response response) {
    return response.statusCode();
  }
}
