/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientWrapUtil.wrapResponseListeners;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyHttpClient9TracingInterceptor;
import java.util.List;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

class TracingHttpClient extends HttpClient {

  private final Instrumenter<Request, Response> instrumenter;

  TracingHttpClient(Instrumenter<Request, Response> instrumenter) {
    super();
    this.instrumenter = instrumenter;
  }

  TracingHttpClient(
      Instrumenter<Request, Response> instrumenter, SslContextFactory sslContextFactory) {
    super(sslContextFactory);
    this.instrumenter = instrumenter;
  }

  TracingHttpClient(
      Instrumenter<Request, Response> instrumenter,
      HttpClientTransport transport,
      SslContextFactory sslContextFactory) {
    super(transport, sslContextFactory);
    this.instrumenter = instrumenter;
  }

  static TracingHttpClient buildNew(
      Instrumenter<Request, Response> instrumenter,
      SslContextFactory sslContextFactory,
      HttpClientTransport httpClientTransport) {
    TracingHttpClient tracingHttpClient = null;
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
  protected void send(HttpRequest request, List<Response.ResponseListener> listeners) {
    Context parentContext = Context.current();
    JettyHttpClient9TracingInterceptor requestInterceptor =
        new JettyHttpClient9TracingInterceptor(parentContext, this.instrumenter);
    requestInterceptor.attachToRequest(request);
    List<Response.ResponseListener> wrapped = wrapResponseListeners(parentContext, listeners);
    super.send(request, wrapped);
  }
}
