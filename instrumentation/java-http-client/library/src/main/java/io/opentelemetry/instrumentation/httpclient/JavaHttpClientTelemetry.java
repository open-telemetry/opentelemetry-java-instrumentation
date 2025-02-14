/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.javahttpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.javahttpclient.internal.OpenTelemetryHttpClient;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Entrypoint for instrumenting Java HTTP Client.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.javahttpclient.JavaHttpClientTelemetry}
 *     instead.
 */
@Deprecated
public final class JavaHttpClientTelemetry {

  /**
   * Returns a new {@link JavaHttpClientTelemetry} configured with the given {@link OpenTelemetry}.
   */
  public static JavaHttpClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

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
   * Construct a new OpenTelemetry tracing-enabled {@link HttpClient} using the provided {@link
   * HttpClient} instance.
   *
   * @param client An instance of HttpClient configured as desired.
   * @return a tracing-enabled {@link HttpClient}.
   */
  public HttpClient newHttpClient(HttpClient client) {
    return new OpenTelemetryHttpClient(client, instrumenter, headersSetter);
  }
}
