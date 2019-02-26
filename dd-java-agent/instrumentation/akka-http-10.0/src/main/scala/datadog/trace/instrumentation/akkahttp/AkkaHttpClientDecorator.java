package datadog.trace.instrumentation.akkahttp;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import datadog.trace.agent.decorator.HttpClientDecorator;

public class AkkaHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {
  public static final AkkaHttpClientDecorator DECORATE = new AkkaHttpClientDecorator();

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
  protected String url(final HttpRequest httpRequest) {
    return httpRequest.uri().toString();
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
