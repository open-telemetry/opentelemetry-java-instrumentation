/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2


import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.ssl.SslContextFactory

class JettyHttpClient9LibraryTest extends AbstractJettyClient9Test implements LibraryTestTrait {


  @Override
  boolean testWithClientParent() {
    //As mentioned in other instrumentation, i.e. OKhttp-3.0, this does not work well in library tests
    false
  }

  @Override
  HttpClient createStandardClient() {
    JettyClientTracingBuilder jettyClientTracingBuilder = new JettyClientTracingBuilder()
    return jettyClientTracingBuilder.setOpenTelemetry(getOpenTelemetry()).build().getHttpClient()
  }

  @Override
  HttpClient createHttpsClient(SslContextFactory sslContextFactory) {
    JettyClientTracingBuilder jettyClientTracingBuilder = new JettyClientTracingBuilder()
    return jettyClientTracingBuilder
      .setOpenTelemetry(getOpenTelemetry())
      .setSslContextFactory(sslContextFactory)
      .build()
      .getHttpClient()
  }
}
