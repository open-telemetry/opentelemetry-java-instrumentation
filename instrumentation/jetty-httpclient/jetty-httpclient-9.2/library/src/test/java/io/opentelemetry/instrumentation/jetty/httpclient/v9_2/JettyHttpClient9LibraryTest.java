/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient9LibraryTest extends AbstractJettyClient9Test {

  @RegisterExtension
  static final InstrumentationExtension extension = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected HttpClient createStandardClient() {
    return JettyClientTelemetry.builder(testing.getOpenTelemetry())
        .setRequestHeaders(AbstractHttpClientTest.TEST_HEADERS)
        .setResponseHeaders(AbstractHttpClientTest.TEST_HEADERS)
        .build()
        .createHttpClient();
  }

  @Override
  protected HttpClient createHttpsClient(SslContextFactory sslContextFactory) {
    return JettyClientTelemetry.builder(testing.getOpenTelemetry())
        .build()
        .createHttpClient(sslContextFactory);
  }
}
