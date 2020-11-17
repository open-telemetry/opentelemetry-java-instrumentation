/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.HeadersInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpUrlConnectionTracer
    extends HttpClientTracer<HttpURLConnection, HttpURLConnection, Integer> {

  private static final HttpUrlConnectionTracer TRACER = new HttpUrlConnectionTracer();

  public static HttpUrlConnectionTracer tracer() {
    return TRACER;
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
  protected Integer status(Integer status) {
    return status;
  }

  @Override
  protected String requestHeader(HttpURLConnection httpUrlConnection, String name) {
    return httpUrlConnection.getRequestProperty(name);
  }

  @Override
  protected String responseHeader(Integer integer, String name) {
    return null;
  }

  @Override
  protected Setter<HttpURLConnection> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.http-url-connection";
  }
}
