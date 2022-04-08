/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.extension.RegisterExtension;

class ApacheHttpClientTest extends AbstractApacheHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected CloseableHttpClient createClient(boolean readTimeout) {
    HttpClientBuilder builder =
        ApacheHttpClientTelemetry.create(testing.getOpenTelemetry()).newHttpClientBuilder();
    RequestConfig.Builder requestConfigBuilder =
        RequestConfig.custom()
            .setMaxRedirects(2)
            .setConnectTimeout((int) AbstractHttpClientTest.CONNECTION_TIMEOUT.toMillis());
    if (readTimeout) {
      requestConfigBuilder.setSocketTimeout((int) AbstractHttpClientTest.READ_TIMEOUT.toMillis());
    }
    builder.setDefaultRequestConfig(requestConfigBuilder.build());
    return builder.build();
  }
}
