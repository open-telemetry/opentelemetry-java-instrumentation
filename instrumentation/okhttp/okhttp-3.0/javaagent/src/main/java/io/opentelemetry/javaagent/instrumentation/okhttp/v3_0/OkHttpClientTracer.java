/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.RequestBuilderInjectAdapter.SETTER;

import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpClientTracer extends HttpClientTracer<Request, Response> {
  private static final OkHttpClientTracer TRACER = new OkHttpClientTracer();

  public static OkHttpClientTracer tracer() {
    return TRACER;
  }

  public Operation startOperation(Request request, Request.Builder builder) {
    return startOperation(request, builder, SETTER, -1);
  }

  @Override
  protected String method(Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected Integer status(Response httpResponse) {
    return httpResponse.code();
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
