/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient

class ApacheClientHostRequestTest extends AbstractApacheClientHostRequestTest implements LibraryTestTrait {
  @Override
  protected CloseableHttpClient createClient() {
    def builder = ApacheHttpClientTracing.create(openTelemetry).newHttpClientBuilder()
    builder.defaultRequestConfig = RequestConfig.custom()
      .setMaxRedirects(maxRedirects())
      .setConnectTimeout(CONNECT_TIMEOUT_MS)
      .build()
    return builder.build()
  }
}
