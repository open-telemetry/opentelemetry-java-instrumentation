/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class TracingHttpClientBuilder {

  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;
  private Instrumenter<Request, Response> instrumenter;

  public TracingHttpClientBuilder setHttpClientTransport(HttpClientTransport httpClientTransport) {
    this.httpClientTransport = httpClientTransport;
    return this;
  }

  public TracingHttpClientBuilder setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  public TracingHttpClientBuilder setInstrumenter(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
    return this;
  }

  public TracingHttpClient build() {
    TracingHttpClient tracingHttpClient = null;
    if (this.sslContextFactory != null && this.httpClientTransport != null) {
      tracingHttpClient = new TracingHttpClient(this.httpClientTransport, this.sslContextFactory);
    } else if (this.sslContextFactory != null) {
      tracingHttpClient = new TracingHttpClient(this.sslContextFactory);
    } else {
      tracingHttpClient = new TracingHttpClient();
    }
    tracingHttpClient.setInstrumenter(this.instrumenter);
    return tracingHttpClient;
  }
}
