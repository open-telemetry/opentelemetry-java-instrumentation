/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2


import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory

class JettyHttpClient9LibraryTest extends AbstractJettyClient9Test implements LibraryTestTrait {

  @Override
  HttpClient createStandardClient() {
    return JettyClientTracing.create(getOpenTelemetry()).getHttpClient()
  }

  @Override
  HttpClient createHttpsClient(SslContextFactory sslContextFactory) {
    return JettyClientTracing.builder(getOpenTelemetry())
      .setSslContextFactory(sslContextFactory)
      .build()
      .getHttpClient()
  }
}
