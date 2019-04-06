package datadog.trace.instrumentation.akkahttp;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import datadog.trace.agent.decorator.HttpServerDecorator;

public class AkkaHttpServerDecorator extends HttpServerDecorator<HttpRequest, HttpResponse> {
  public static final AkkaHttpServerDecorator DECORATE = new AkkaHttpServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"akka-http", "akka-http-server"};
  }

  @Override
  protected String component() {
    return "akka-http-server";
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
  protected String peerHostname(final HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected String peerHostIP(final HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected Integer peerPort(final HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }
}
