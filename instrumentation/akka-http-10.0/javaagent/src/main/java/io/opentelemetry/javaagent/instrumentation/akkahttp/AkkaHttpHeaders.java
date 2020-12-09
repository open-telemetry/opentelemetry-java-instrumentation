package io.opentelemetry.javaagent.instrumentation.akkahttp;

import akka.http.scaladsl.model.HttpRequest;

public class AkkaHttpHeaders {
  private HttpRequest request;

  public AkkaHttpHeaders(HttpRequest request) {
    this.request = request;
  }

  public HttpRequest getRequest() {
    return request;
  }

  public void setRequest(HttpRequest request) {
    this.request = request;
  }
}
