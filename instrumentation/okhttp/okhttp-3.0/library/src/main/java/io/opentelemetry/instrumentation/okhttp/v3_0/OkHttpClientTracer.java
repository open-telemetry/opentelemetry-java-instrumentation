/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static io.opentelemetry.instrumentation.okhttp.v3_0.RequestBuilderInjectAdapter.SETTER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

final class OkHttpClientTracer extends HttpClientTracer<Request, Request.Builder, Response> {

  OkHttpClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry, NetPeerAttributes.INSTANCE);
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
