package datadog.trace.instrumentation.googlehttpclient;

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
    // Google uses %20 (space) instead of "+" for spaces in the fragment
    // Add "+" back for consistency with the other http client instrumentations
    final String url = httpRequest.getUrl().build();
    final String fixedUrl = url.replaceAll("%20", "+");
    return new URI(fixedUrl);
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
