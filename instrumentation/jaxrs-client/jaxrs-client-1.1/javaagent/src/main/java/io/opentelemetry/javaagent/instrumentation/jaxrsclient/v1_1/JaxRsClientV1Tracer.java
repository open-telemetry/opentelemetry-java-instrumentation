/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1.InjectAdapter.SETTER;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;

public class JaxRsClientV1Tracer
    extends HttpClientTracer<ClientRequest, ClientRequest, ClientResponse> {
  private static final JaxRsClientV1Tracer TRACER = new JaxRsClientV1Tracer();

  public static JaxRsClientV1Tracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(ClientRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(ClientRequest httpRequest) {
    return httpRequest.getURI();
  }

  @Override
  protected Integer status(ClientResponse clientResponse) {
    return clientResponse.getStatus();
  }

  @Override
  protected String requestHeader(ClientRequest clientRequest, String name) {
    Object header = clientRequest.getHeaders().getFirst(name);
    return header != null ? header.toString() : null;
  }

  @Override
  protected String responseHeader(ClientResponse clientResponse, String name) {
    return clientResponse.getHeaders().getFirst(name);
  }

  @Override
  protected TextMapSetter<ClientRequest> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxrs-client-1.1";
  }
}
