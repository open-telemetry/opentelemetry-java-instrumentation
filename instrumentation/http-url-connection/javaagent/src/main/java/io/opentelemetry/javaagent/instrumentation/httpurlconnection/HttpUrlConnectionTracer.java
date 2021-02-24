/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.HeadersInjectAdapter.SETTER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpUrlConnectionTracer
    extends HttpClientTracer<HttpURLConnection, HttpURLConnection, HttpUrlResponse> {

  private static final HttpUrlConnectionTracer TRACER = new HttpUrlConnectionTracer();

  public static HttpUrlConnectionTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, HttpURLConnection request) {
    return super.startSpan(parentContext, request, request);
  }

  @Override
  protected String method(HttpURLConnection connection) {
    return connection.getRequestMethod();
  }

  @Override
  protected URI url(HttpURLConnection connection) throws URISyntaxException {
    return connection.getURL().toURI();
  }

  @Override
  protected Integer status(HttpUrlResponse response) {
    return response.status();
  }

  @Override
  protected String requestHeader(HttpURLConnection httpUrlConnection, String name) {
    return httpUrlConnection.getRequestProperty(name);
  }

  @Override
  protected String responseHeader(HttpUrlResponse response, String name) {
    return response.header(name);
  }

  @Override
  protected TextMapSetter<HttpURLConnection> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.http-url-connection";
  }
}
