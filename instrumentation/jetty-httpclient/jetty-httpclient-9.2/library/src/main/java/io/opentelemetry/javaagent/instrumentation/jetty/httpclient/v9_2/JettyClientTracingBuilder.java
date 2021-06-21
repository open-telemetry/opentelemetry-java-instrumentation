/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettyClientTracingBuilder {

  private OpenTelemetry openTelemetry;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  public JettyClientTracingBuilder setOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    return this;
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

    TracingHttpClientBuilder tracingHttpClientBuilder = new TracingHttpClientBuilder();
    tracingHttpClientBuilder
        .setInstrumenter(instrumenter)
        .setHttpClientTransport(this.httpClientTransport)
        .setSslContextFactory(this.sslContextFactory);

    return new JettyClientTracing(instrumenter, tracingHttpClientBuilder.build());
  }
}
