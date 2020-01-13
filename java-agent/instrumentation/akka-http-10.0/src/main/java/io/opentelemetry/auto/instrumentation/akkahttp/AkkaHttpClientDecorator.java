package io.opentelemetry.auto.instrumentation.akkahttp;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;

public class AkkaHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {
  public static final AkkaHttpClientDecorator DECORATE = new AkkaHttpClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"akka-http", "akka-http-client"};
  }

  @Override
  protected String component() {
    return "akka-http-client";
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  protected URI url(final HttpRequest httpRequest) throws URISyntaxException {
    return new URI(httpRequest.uri().toString());
  }

  @Override
  protected String hostname(final HttpRequest httpRequest) {
    return httpRequest.getUri().host().address();
  }

  @Override
  protected Integer port(final HttpRequest httpRequest) {
    return httpRequest.getUri().port();
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }
}
