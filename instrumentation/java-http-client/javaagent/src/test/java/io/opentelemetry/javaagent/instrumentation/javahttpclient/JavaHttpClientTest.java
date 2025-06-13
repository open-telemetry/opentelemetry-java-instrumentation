/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpclient;

import io.opentelemetry.instrumentation.javahttpclient.AbstractJavaHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.net.http.HttpClient;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JavaHttpClientTest extends AbstractJavaHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected HttpClient configureHttpClient(HttpClient httpClient) {
    return httpClient;
  }
}
