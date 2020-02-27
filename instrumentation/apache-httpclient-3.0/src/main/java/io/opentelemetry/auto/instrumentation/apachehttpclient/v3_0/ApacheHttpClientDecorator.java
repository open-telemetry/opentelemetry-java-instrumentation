package io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.URIException;

public class ApacheHttpClientDecorator extends HttpClientDecorator<HttpMethod, HttpMethod> {
  public static final ApacheHttpClientDecorator DECORATE = new ApacheHttpClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.apache-httpclient-3.0");

  @Override
  protected String getComponentName() {
    return "apache-httpclient";
  }

  @Override
  protected String method(final HttpMethod httpMethod) {
    return httpMethod.getName();
  }

  @Override
  protected URI url(final HttpMethod httpMethod) throws URISyntaxException {
    final org.apache.commons.httpclient.URI uri;
    try {
      uri = httpMethod.getURI();
    } catch (final URIException e) {
      return null;
    }
    return new URI(uri.toString());
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
