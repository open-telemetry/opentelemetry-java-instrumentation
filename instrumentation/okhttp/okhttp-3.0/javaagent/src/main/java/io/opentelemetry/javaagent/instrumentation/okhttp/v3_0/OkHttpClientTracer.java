/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.RequestBuilderInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpClientTracer extends HttpClientTracer<Request, Request.Builder, Response> {
  private static final OkHttpClientTracer TRACER = new OkHttpClientTracer();

  public static OkHttpClientTracer tracer() {
    return TRACER;
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
  protected TextMapSetter<Request.Builder> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.okhttp-3.0";
  }
}
