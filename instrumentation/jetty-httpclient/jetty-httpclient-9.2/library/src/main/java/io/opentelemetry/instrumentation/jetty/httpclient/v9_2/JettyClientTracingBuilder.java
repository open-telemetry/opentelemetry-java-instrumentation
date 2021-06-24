/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterBuilder;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public final class JettyClientTracingBuilder {

  private final OpenTelemetry openTelemetry;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  public JettyClientTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public JettyClientTracingBuilder setHttpClientTransport(HttpClientTransport httpClientTransport) {
    this.httpClientTransport = httpClientTransport;
    return this;
  }

  public JettyClientTracingBuilder setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  public JettyClientTracing build() {
    JettyClientInstrumenterBuilder instrumenterBuilder =
        new JettyClientInstrumenterBuilder(this.openTelemetry);
    Instrumenter<Request, Response> instrumenter = instrumenterBuilder.build();

    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew(instrumenter, this.sslContextFactory, this.httpClientTransport);

    return new JettyClientTracing(tracingHttpClient);
  }
}
