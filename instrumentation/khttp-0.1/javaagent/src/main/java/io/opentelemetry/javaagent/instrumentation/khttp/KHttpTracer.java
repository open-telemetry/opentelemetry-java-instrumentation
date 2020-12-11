/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.khttp;

import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import khttp.responses.Response;

public class KHttpTracer extends HttpClientTracer<RequestWrapper, Response> {
  private static final KHttpTracer TRACER = new KHttpTracer();

  public static KHttpTracer tracer() {
    return TRACER;
  }

  public Operation startOperation(RequestWrapper request, Map<String, String> headers) {
    return super.startOperation(request, headers, SETTER);
  }

  @Override
  protected String method(RequestWrapper requestWrapper) {
    return requestWrapper.method;
  }

  @Override
  protected URI url(RequestWrapper requestWrapper) throws URISyntaxException {
    return new URI(requestWrapper.uri);
  }

  @Override
  protected Integer status(Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String requestHeader(RequestWrapper requestWrapper, String name) {
    return requestWrapper.headers.get(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.getHeaders().get(name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.khttp";
  }
}
