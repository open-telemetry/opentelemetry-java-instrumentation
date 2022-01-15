/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient

class ApacheClientHostRequestContextTest extends AbstractApacheClientHostRequestContextTest implements LibraryTestTrait {
  @Override
  protected CloseableHttpClient createClient(boolean readTimeout) {
    def builder = ApacheHttpClientTracing.create(openTelemetry).newHttpClientBuilder()
    def requestConfigBuilder = RequestConfig.custom()
      .setMaxRedirects(maxRedirects())
      .setConnectTimeout(CONNECT_TIMEOUT_MS)
    if (readTimeout) {
      requestConfigBuilder.setSocketTimeout(READ_TIMEOUT_MS)
    }
    builder.defaultRequestConfig = requestConfigBuilder.build()
    return builder.build()
  }
}
