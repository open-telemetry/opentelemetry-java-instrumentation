/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

public class AsyncHttpClientTracer extends HttpClientTracer<Request, Request, Response> {

  private static final AsyncHttpClientTracer TRACER = new AsyncHttpClientTracer();

  public static AsyncHttpClientTracer tracer() {
    return TRACER;
  }

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
    return request.getHeaders().get(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.getHeaders().get(name);
  }

  @Override
  protected TextMapSetter<Request> getSetter() {
    return AsyncHttpClientInjectAdapter.SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.async-http-client-2.0";
  }
}
