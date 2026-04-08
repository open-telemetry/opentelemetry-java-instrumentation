/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient12LibraryTest extends AbstractJettyClient12Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected HttpClient createStandardClient() {
    return JettyClientTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .createHttpClient();
  }

  @Override
  protected HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory) {
    HttpClient client =
        JettyClientTelemetry.builder(testing.getOpenTelemetry()).build().createHttpClient();
    client.setSslContextFactory(sslContextFactory);
    return client;
  }
}
