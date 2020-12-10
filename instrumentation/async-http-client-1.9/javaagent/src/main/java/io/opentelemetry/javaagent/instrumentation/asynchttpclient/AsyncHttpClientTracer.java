/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.AsyncHttpClientInjectAdapter.SETTER;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class AsyncHttpClientTracer extends HttpClientTracer<Request, Response> {

  private static final AsyncHttpClientTracer TRACER = new AsyncHttpClientTracer();

  public static AsyncHttpClientTracer tracer() {
    return TRACER;
  }

  public HttpClientOperation startOperation(Request request) {
    return super.startOperation(request, SETTER);
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
    return request.getHeaders().getFirstValue(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.getHeaders().getFirstValue(name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.async-http-client";
  }
}
