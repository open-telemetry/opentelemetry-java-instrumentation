/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.AbstractJettyClient12Test;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient12AgentTest extends AbstractJettyClient12Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected HttpClient createStandardClient() {
    return new HttpClient();
  }

  @Override
  protected HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory) {
    HttpClient httpClient = new HttpClient();
    httpClient.setSslContextFactory(sslContextFactory);
    return httpClient;
  }
}
