/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v2_2.RequestBuilderInjectAdapter.SETTER;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.net.URI;
import java.net.URISyntaxException;

public class OkHttpClientTracer extends HttpClientTracer<Request, Response> {
  private static final OkHttpClientTracer TRACER = new OkHttpClientTracer();

  public static OkHttpClientTracer tracer() {
    return TRACER;
  }

  public Operation<Response> startOperation(Request request, Request.Builder builder) {
    return super.startOperation(request, builder, SETTER);
  }

  @Override
  protected String method(Request request) {
    return request.method();
  }

  @Override
  protected URI url(Request request) throws URISyntaxException {
    return request.url().toURI();
  }

  @Override
  protected Integer status(Response response) {
    return response.code();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.header(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.header(name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.okhttp";
  }
}
