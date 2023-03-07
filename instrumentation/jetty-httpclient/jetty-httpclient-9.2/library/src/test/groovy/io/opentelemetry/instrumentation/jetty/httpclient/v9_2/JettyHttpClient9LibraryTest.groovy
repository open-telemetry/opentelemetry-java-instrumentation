/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2


import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory

class JettyHttpClient9LibraryTest extends AbstractJettyClient9Test implements LibraryTestTrait {

  @Override
  HttpClient createStandardClient() {
    return JettyClientTelemetry.builder(getOpenTelemetry())
      .setCapturedRequestHeaders([AbstractHttpClientTest.TEST_REQUEST_HEADER])
      .setCapturedResponseHeaders([AbstractHttpClientTest.TEST_RESPONSE_HEADER])
      .build().getHttpClient()
  }

  @Override
  HttpClient createHttpsClient(SslContextFactory sslContextFactory) {
    return JettyClientTelemetry.builder(getOpenTelemetry())
      .setSslContextFactory(sslContextFactory)
      .build()
      .getHttpClient()
  }
}
