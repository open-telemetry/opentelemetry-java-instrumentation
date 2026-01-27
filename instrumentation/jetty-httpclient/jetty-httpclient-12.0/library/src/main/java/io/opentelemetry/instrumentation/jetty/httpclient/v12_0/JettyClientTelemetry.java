/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

/** Entrypoint for instrumenting Jetty client. */
public final class JettyClientTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static JettyClientTelemetry create(OpenTelemetry openTelemetry) {
    JettyClientTelemetryBuilder builder = builder(openTelemetry);
    return builder.build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static JettyClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JettyClientTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> instrumenter;

  JettyClientTelemetry(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /**
   * Returns an instrumented HTTP client.
   *
   * @deprecated Use {@link #createHttpClient()} instead.
   */
  @Deprecated
  public HttpClient newHttpClient() {
    return createHttpClient();
  }

  /**
   * Returns a new {@link HttpClient} with the specified transport and tracing configured.
   *
   * @param httpClientTransport the HTTP client transport to use
   * @deprecated Use {@link #createHttpClient(HttpClientTransport)} instead.
   */
  @Deprecated
  public HttpClient newHttpClient(HttpClientTransport httpClientTransport) {
    return createHttpClient(httpClientTransport);
  }

  /** Returns an instrumented HTTP client. */
  public HttpClient createHttpClient() {
    return new TracingHttpClient(instrumenter);
  }

  /**
   * Returns a new {@link HttpClient} with the specified transport and tracing configured.
   *
   * @param httpClientTransport the HTTP client transport to use
   */
  public HttpClient createHttpClient(HttpClientTransport httpClientTransport) {
    return new TracingHttpClient(instrumenter, httpClientTransport);
  }
}
