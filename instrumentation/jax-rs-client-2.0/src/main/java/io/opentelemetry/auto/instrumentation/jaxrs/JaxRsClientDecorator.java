package io.opentelemetry.auto.instrumentation.jaxrs;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

public class JaxRsClientDecorator
    extends HttpClientDecorator<ClientRequestContext, ClientResponseContext> {
  public static final JaxRsClientDecorator DECORATE = new JaxRsClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String getComponentName() {
    return "jax-rs.client";
  }

  @Override
  protected String method(final ClientRequestContext httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final ClientRequestContext httpRequest) {
    return httpRequest.getUri();
  }

  @Override
  protected String hostname(final ClientRequestContext httpRequest) {
    return httpRequest.getUri().getHost();
  }

  @Override
  protected Integer port(final ClientRequestContext httpRequest) {
    return httpRequest.getUri().getPort();
  }

  @Override
  protected Integer status(final ClientResponseContext httpResponse) {
    return httpResponse.getStatus();
  }
}
