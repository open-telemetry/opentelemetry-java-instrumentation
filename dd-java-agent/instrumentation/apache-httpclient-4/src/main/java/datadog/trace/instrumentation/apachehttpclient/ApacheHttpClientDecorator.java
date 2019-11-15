package datadog.trace.instrumentation.apachehttpclient;

import datadog.trace.agent.decorator.HttpClientDecorator;
import java.net.URI;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientDecorator extends HttpClientDecorator<HttpUriRequest, HttpResponse> {
  public static final ApacheHttpClientDecorator DECORATE = new ApacheHttpClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"httpclient", "apache-httpclient", "apache-http-client"};
  }

  @Override
  protected String component() {
    return "apache-httpclient";
  }

  @Override
  protected String method(final HttpUriRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final HttpUriRequest request) {
    return request.getURI();
  }

  @Override
  protected String hostname(final HttpUriRequest httpRequest) {
    final URI uri = httpRequest.getURI();
    if (uri != null) {
      return uri.getHost();
    } else {
      return null;
    }
  }

  @Override
  protected Integer port(final HttpUriRequest httpRequest) {
    final URI uri = httpRequest.getURI();
    if (uri != null) {
      return uri.getPort();
    } else {
      return null;
    }
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.getStatusLine().getStatusCode();
  }
}
