package datadog.trace.instrumentation.apachehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import datadog.trace.agent.decorator.HttpClientDecorator;
import java.net.URI;
import java.net.URISyntaxException;

public class GoogleHttpClientDecorator extends HttpClientDecorator<HttpRequest, HttpResponse> {
  public static final GoogleHttpClientDecorator DECORATE = new GoogleHttpClientDecorator();

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  protected URI url(final HttpRequest httpRequest) throws URISyntaxException {
    return httpRequest.getUrl().toURI();
  }

  @Override
  protected String hostname(final HttpRequest httpRequest) {
    return httpRequest.getUrl().getHost();
  }

  @Override
  protected Integer port(final HttpRequest httpRequest) {
    return httpRequest.getUrl().getPort();
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"google-http-client"};
  }

  @Override
  protected String component() {
    return "google-http-client";
  }
}
