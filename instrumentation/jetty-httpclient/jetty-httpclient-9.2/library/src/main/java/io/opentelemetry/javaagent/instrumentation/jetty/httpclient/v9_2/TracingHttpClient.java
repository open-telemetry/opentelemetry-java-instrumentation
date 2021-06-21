/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyClientWrapUtil.wrapResponseListeners;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

class TracingHttpClient extends HttpClient {

  private Instrumenter<Request, Response> instrumenter;

  TracingHttpClient() {}

  TracingHttpClient(SslContextFactory sslContextFactory) {
    super(sslContextFactory);
  }

  TracingHttpClient(HttpClientTransport transport, SslContextFactory sslContextFactory) {
    super(transport, sslContextFactory);
  }

  public void setInstrumenter(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
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
