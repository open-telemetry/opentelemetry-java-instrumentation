/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;

public class AkkaHttpServerTracer
    extends HttpServerTracer<HttpRequest, HttpResponse, HttpRequest, Void> {
  private static final AkkaHttpServerTracer TRACER = new AkkaHttpServerTracer();

  public static AkkaHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeader(name).map(HttpHeader::value).orElse(null);
  }

  @Override
  protected int responseStatus(HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }

  @Override
  protected void attachServerContext(Context context, Void none) {}

  @Override
  public Context getServerContext(Void none) {
    return null;
  }

  @Override
  protected String url(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  protected String peerHostIP(HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected String flavor(HttpRequest connection, HttpRequest request) {
    return connection.protocol().value();
  }

  @Override
  protected TextMapGetter<HttpRequest> getGetter() {
    return AkkaHttpServerHeaders.GETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.akka-http-10.0";
  }

  @Override
  protected Integer peerPort(HttpRequest httpRequest) {
    return null;
  }
}
