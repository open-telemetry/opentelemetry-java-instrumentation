package datadog.trace.instrumentation.commonshttpclient;

import datadog.trace.agent.decorator.HttpClientDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.URIException;

public class CommonsHttpClientDecorator extends HttpClientDecorator<HttpMethod, HttpMethod> {
  public static final CommonsHttpClientDecorator DECORATE = new CommonsHttpClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"commons-http-client"};
  }

  @Override
  protected String component() {
    return "commons-http-client";
  }

  @Override
  protected String method(final HttpMethod httpMethod) {
    return httpMethod.getName();
  }

  @Override
  protected URI url(final HttpMethod httpMethod) throws URISyntaxException {
    try {
      //  org.apache.commons.httpclient.URI -> java.net.URI
      return new URI(httpMethod.getURI().toString());
    } catch (final URIException e) {
      throw new URISyntaxException("", e.getMessage());
    }
  }

  @Override
  protected String hostname(final HttpMethod httpMethod) {
    try {
      return httpMethod.getURI().getHost();
    } catch (final URIException e) {
      return null;
    }
  }

  @Override
  protected Integer port(final HttpMethod httpMethod) {
    try {
      return httpMethod.getURI().getPort();
    } catch (final URIException e) {
      return null;
    }
  }

  @Override
  protected Integer status(final HttpMethod httpMethod) {
    final StatusLine statusLine = httpMethod.getStatusLine();
    return statusLine == null ? null : statusLine.getStatusCode();
  }
}
