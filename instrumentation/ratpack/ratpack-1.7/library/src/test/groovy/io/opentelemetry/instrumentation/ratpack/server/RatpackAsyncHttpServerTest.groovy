/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.instrumentation.ratpack.RatpackTelemetry
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import ratpack.server.RatpackServerSpec

class RatpackAsyncHttpServerTest extends AbstractRatpackAsyncHttpServerTest implements LibraryTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
    RatpackTelemetry telemetry = RatpackTelemetry.builder(openTelemetry)
      .setCapturedServerRequestHeaders([AbstractHttpServerTest.TEST_REQUEST_HEADER])
      .setCapturedServerResponseHeaders([AbstractHttpServerTest.TEST_RESPONSE_HEADER])
      .build()
    serverSpec.registryOf {
      telemetry.configureServerRegistry(it)
    }
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    false
  }
}
