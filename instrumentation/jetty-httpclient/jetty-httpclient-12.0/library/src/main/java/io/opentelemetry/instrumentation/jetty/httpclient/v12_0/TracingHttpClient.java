/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.URI;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.util.ssl.SslContextFactory;

class TracingHttpClient extends HttpClient {

  private final Instrumenter<Request, Response> instrumenter;

  TracingHttpClient(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  TracingHttpClient(
      Instrumenter<Request, Response> instrumenter, SslContextFactory.Client sslContextFactory) {
    setSslContextFactory(sslContextFactory);
    this.instrumenter = instrumenter;
  }

  TracingHttpClient(
      Instrumenter<Request, Response> instrumenter,
      HttpClientTransport transport,
      SslContextFactory.Client sslContextFactory) {
    super(transport);
    setSslContextFactory(sslContextFactory);
    this.instrumenter = instrumenter;
  }

  static TracingHttpClient buildNew(
      Instrumenter<Request, Response> instrumenter,
      SslContextFactory.Client sslContextFactory,
      HttpClientTransport httpClientTransport) {
    TracingHttpClient tracingHttpClient;
    if (sslContextFactory != null && httpClientTransport != null) {
      tracingHttpClient =
          new TracingHttpClient(instrumenter, httpClientTransport, sslContextFactory);
    } else if (sslContextFactory != null) {
      tracingHttpClient = new TracingHttpClient(instrumenter, sslContextFactory);
    } else {
      tracingHttpClient = new TracingHttpClient(instrumenter);
    }
    return tracingHttpClient;
  }

  @Override
  public Request newRequest(URI uri) {
    return new TracingHttpRequest(this, new HttpConversation(), uri, instrumenter);
  }
}
