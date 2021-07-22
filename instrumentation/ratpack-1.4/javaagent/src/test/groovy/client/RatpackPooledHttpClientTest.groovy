/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.testing.junit.SingleConnection
import ratpack.http.client.HttpClient

class RatpackPooledHttpClientTest extends RatpackHttpClientTest {

  @Override
  HttpClient buildHttpClient() {
    return buildHttpClient({spec ->
      spec.poolSize(5)
    })
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    // this test is already run for RatpackHttpClientTest
    // returning null here to avoid running the same test twice
    return null
  }
}
