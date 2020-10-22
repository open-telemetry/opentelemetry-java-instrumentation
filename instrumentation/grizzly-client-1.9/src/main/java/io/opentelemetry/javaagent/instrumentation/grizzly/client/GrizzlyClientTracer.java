/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class GrizzlyClientTracer extends HttpClientTracer<Request, Request, Response> {

  public static final GrizzlyClientTracer TRACER = new GrizzlyClientTracer();

  @Override
  protected String method(Request request) {
    return request.getMethod();
  }

  @Override
  protected URI url(Request request) throws URISyntaxException {
    return request.getUri().toJavaNetURI();
  }

  @Override
  protected Integer status(Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.getHeaders().getFirstValue(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.getHeaders().getFirstValue(name);
  }

  @Override
  protected Setter<Request> getSetter() {
    return GrizzlyInjectAdapter.SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grizzly-client";
  }
}
