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
  boolean testWithClientParent() {
    //The client parent test does not work well in the context of library only tests.
    false
  }

  @Override
  HttpClient createStandardClient() {
    JettyClientTracingBuilder jettyClientTracingBuilder = new JettyClientTracingBuilder(getOpenTelemetry())
    return jettyClientTracingBuilder.build().getHttpClient()
  }

  @Override
  HttpClient createHttpsClient(SslContextFactory sslContextFactory) {
    JettyClientTracingBuilder jettyClientTracingBuilder = new JettyClientTracingBuilder(getOpenTelemetry())
    return jettyClientTracingBuilder
      .setSslContextFactory(sslContextFactory)
      .build()
      .getHttpClient()
  }
}
