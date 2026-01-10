/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/** Entrypoint for instrumenting Jetty client. */
public final class JettyClientTelemetry {

  /** Returns a new {@link JettyClientTelemetry} configured with the given {@link OpenTelemetry}. */
  public static JettyClientTelemetry create(OpenTelemetry openTelemetry) {
    JettyClientTelemetryBuilder builder = builder(openTelemetry);
    return builder.build();
  }

  /**
   * Returns a new {@link JettyClientTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static JettyClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JettyClientTelemetryBuilder(openTelemetry);
  }

  private final HttpClient httpClient;
  private final Instrumenter<Request, Response> instrumenter;

  JettyClientTelemetry(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.instrumenter = null;
  }

  JettyClientTelemetry(HttpClient httpClient, Instrumenter<Request, Response> instrumenter) {
    this.httpClient = httpClient;
    this.instrumenter = instrumenter;
  }

  /**
   * @deprecated Use {@link #newHttpClient()}, {@link #newHttpClient(SslContextFactory)}, or {@link
   *     #newHttpClient(HttpClientTransport, SslContextFactory)} instead.
   */
  @Deprecated
  public HttpClient getHttpClient() {
    return httpClient;
  }

  /** Returns a new {@link HttpClient} with tracing configured. */
  public HttpClient newHttpClient() {
    return new TracingHttpClient(instrumenter);
  }

  /**
   * Returns a new {@link HttpClient} with the specified SSL context factory and tracing configured.
   *
   * @param sslContextFactory the SSL context factory to use for HTTPS support
   */
  public HttpClient newHttpClient(SslContextFactory sslContextFactory) {
    return new TracingHttpClient(instrumenter, sslContextFactory);
  }

  /**
   * Returns a new {@link HttpClient} with the specified transport and SSL context factory and
   * tracing configured.
   *
   * @param httpClientTransport the HTTP client transport to use
   * @param sslContextFactory the SSL context factory to use
   */
  public HttpClient newHttpClient(
      HttpClientTransport httpClientTransport, SslContextFactory sslContextFactory) {
    return new TracingHttpClient(instrumenter, httpClientTransport, sslContextFactory);
  }
}
