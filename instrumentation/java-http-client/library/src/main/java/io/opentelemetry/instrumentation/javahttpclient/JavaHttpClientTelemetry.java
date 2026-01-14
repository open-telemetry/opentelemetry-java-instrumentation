/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.javahttpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.javahttpclient.internal.OpenTelemetryHttpClient;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Entrypoint for instrumenting Java HTTP Client. */
public final class JavaHttpClientTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static JavaHttpClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static JavaHttpClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JavaHttpClientTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpRequest, HttpResponse<?>> instrumenter;
  private final HttpHeadersSetter headersSetter;

  JavaHttpClientTelemetry(
      Instrumenter<HttpRequest, HttpResponse<?>> instrumenter, HttpHeadersSetter headersSetter) {
    this.instrumenter = instrumenter;
    this.headersSetter = headersSetter;
  }

  /**
   * Returns an instrumented {@link HttpClient} wrapping the provided client.
   *
   * @param client the HttpClient to wrap
   * @return an instrumented HttpClient
   */
  public HttpClient createHttpClient(HttpClient client) {
    return new OpenTelemetryHttpClient(client, instrumenter, headersSetter);
  }

  /**
   * Returns an instrumented {@link HttpClient} wrapping the provided client.
   *
   * @param client the HttpClient to wrap
   * @return an instrumented HttpClient
   * @deprecated Use {@link #createHttpClient(HttpClient)} instead.
   */
  @Deprecated
  public HttpClient newHttpClient(HttpClient client) {
    return createHttpClient(client);
  }
}
