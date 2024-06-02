/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.AbstractJettyClient12Test;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettyHttpClient12AgentTest extends AbstractJettyClient12Test {

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
