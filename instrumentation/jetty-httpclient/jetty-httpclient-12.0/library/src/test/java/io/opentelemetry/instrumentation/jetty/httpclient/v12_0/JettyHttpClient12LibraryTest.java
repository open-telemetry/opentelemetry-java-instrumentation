/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.Collections;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient12LibraryTest extends AbstractJettyClient12Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected HttpClient createStandardClient() {
    return JettyClientTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(
            Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(
            Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .getHttpClient();
  }

  @Override
  protected HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory) {
    return JettyClientTelemetry.builder(testing.getOpenTelemetry())
        .setSslContextFactory(sslContextFactory)
        .build()
        .getHttpClient();
  }
}
