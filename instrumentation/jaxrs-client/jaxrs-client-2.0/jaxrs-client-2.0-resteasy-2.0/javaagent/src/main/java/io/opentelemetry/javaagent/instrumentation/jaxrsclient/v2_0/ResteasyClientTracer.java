/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.ResteasyInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

public class ResteasyClientTracer
    extends HttpClientTracer<ClientInvocation, ClientInvocation, Response> {
  private static final ResteasyClientTracer TRACER = new ResteasyClientTracer();

  public static ResteasyClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(ClientInvocation httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(ClientInvocation httpRequest) {
    return httpRequest.getUri();
  }

  @Override
  protected Integer status(Response httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  protected String requestHeader(ClientInvocation clientRequestContext, String name) {
    return clientRequestContext.getHeaders().getHeader(name);
  }

  @Override
  protected String responseHeader(Response httpResponse, String name) {
    return httpResponse.getHeaderString(name);
  }

  @Override
  protected TextMapSetter<ClientInvocation> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxrs-client-2.0-resteasy-2.0";
  }
}
