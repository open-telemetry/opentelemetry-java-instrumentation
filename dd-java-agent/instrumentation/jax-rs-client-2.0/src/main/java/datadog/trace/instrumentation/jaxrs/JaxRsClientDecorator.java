package datadog.trace.instrumentation.jaxrs;

import datadog.trace.agent.decorator.HttpClientDecorator;
import java.net.URI;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

public class JaxRsClientDecorator
    extends HttpClientDecorator<ClientRequestContext, ClientResponseContext> {
  public static final JaxRsClientDecorator DECORATE = new JaxRsClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jax-rs", "jaxrs", "jax-rs-client"};
  }

  @Override
  protected String component() {
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
