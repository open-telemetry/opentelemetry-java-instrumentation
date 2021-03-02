/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp;

import static io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpClientInstrumentationModule.InjectAdapter.SETTER;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpClientInstrumentationModule.AkkaHttpHeaders;
import java.net.URI;
import java.net.URISyntaxException;

public class AkkaHttpClientTracer
    extends HttpClientTracer<HttpRequest, AkkaHttpHeaders, HttpResponse> {
  private static final AkkaHttpClientTracer TRACER = new AkkaHttpClientTracer();

  public static AkkaHttpClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  protected URI url(HttpRequest httpRequest) throws URISyntaxException {
    return new URI(httpRequest.uri().toString());
  }

  @Override
  protected String flavor(HttpRequest httpRequest) {
    return httpRequest.protocol().value();
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeader(name).map(HttpHeader::value).orElse(null);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.getHeader(name).map(HttpHeader::value).orElse(null);
  }

  @Override
  protected TextMapSetter<AkkaHttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.akka-http-10.0";
  }
}
