package io.opentelemetry.auto.instrumentation.jaxrs.v1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;

public class JaxRsClientV1Decorator extends HttpClientDecorator<ClientRequest, ClientResponse> {
  public static final JaxRsClientV1Decorator DECORATE = new JaxRsClientV1Decorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.jax-rs-client-1.1");

  @Override
  protected String getComponentName() {
    return "jax-rs.client";
  }

  @Override
  protected String method(final ClientRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final ClientRequest httpRequest) {
    return httpRequest.getURI();
  }

  @Override
  protected String hostname(final ClientRequest httpRequest) {
    return httpRequest.getURI().getHost();
  }

  @Override
  protected Integer port(final ClientRequest httpRequest) {
    return httpRequest.getURI().getPort();
  }

  @Override
  protected Integer status(final ClientResponse clientResponse) {
    return clientResponse.getStatus();
  }
}
