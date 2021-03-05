/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.httpclients;

import static io.opentelemetry.instrumentation.spring.httpclients.HttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

class RestTemplateTracer extends HttpClientTracer<HttpRequest, HttpHeaders, ClientHttpResponse> {

  private static final RestTemplateTracer TRACER = new RestTemplateTracer();

  public static RestTemplateTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected URI url(HttpRequest request) {
    return request.getURI();
  }

  @Override
  protected Integer status(ClientHttpResponse response) {
    try {
      return response.getStatusCode().value();
    } catch (IOException e) {
      return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
  }

  @Override
  protected String requestHeader(HttpRequest request, String name) {
    return request.getHeaders().getFirst(name);
  }

  @Override
  protected String responseHeader(ClientHttpResponse response, String name) {
    return response.getHeaders().getFirst(name);
  }

  @Override
  protected TextMapSetter<HttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-web-3.1";
  }
}
