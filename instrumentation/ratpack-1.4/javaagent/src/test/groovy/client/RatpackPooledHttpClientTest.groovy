/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import ratpack.http.client.HttpClientSpec

class RatpackPooledHttpClientTest extends RatpackHttpClientTest {

  @Override
  void configureClient(HttpClientSpec spec) {
    spec.poolSize(5)
  }
}
