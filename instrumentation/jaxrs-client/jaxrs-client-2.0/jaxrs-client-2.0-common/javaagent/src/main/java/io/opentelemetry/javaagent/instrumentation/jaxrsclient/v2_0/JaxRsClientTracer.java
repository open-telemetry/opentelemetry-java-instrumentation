/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.InjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import java.net.URI;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

public class JaxRsClientTracer
    extends HttpClientTracer<ClientRequestContext, ClientRequestContext, ClientResponseContext> {
  private static final JaxRsClientTracer TRACER = new JaxRsClientTracer();

  private JaxRsClientTracer() {
    super(NetPeerAttributes.INSTANCE);
  }

  public static JaxRsClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(ClientRequestContext httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(ClientRequestContext httpRequest) {
    return httpRequest.getUri();
  }

  @Override
  protected Integer status(ClientResponseContext httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  protected String requestHeader(ClientRequestContext clientRequestContext, String name) {
    return clientRequestContext.getHeaderString(name);
  }

  @Override
  protected String responseHeader(ClientResponseContext clientResponseContext, String name) {
    return clientResponseContext.getHeaderString(name);
  }

  @Override
  protected TextMapSetter<ClientRequestContext> getSetter() {
    return SETTER;
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof ProcessingException) {
      throwable = throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxrs-client-2.0";
  }
}
