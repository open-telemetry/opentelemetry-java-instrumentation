/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.AbstractJettyClient9Test;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient9AgentTest extends AbstractJettyClient9Test {

  @RegisterExtension
  static final InstrumentationExtension extension = HttpClientInstrumentationExtension.forAgent();

  @Override
  public HttpClient createStandardClient() {
    return new HttpClient();
  }

  @Override
  public HttpClient createHttpsClient(SslContextFactory sslContextFactory) {
    return new HttpClient(sslContextFactory);
  }
}
