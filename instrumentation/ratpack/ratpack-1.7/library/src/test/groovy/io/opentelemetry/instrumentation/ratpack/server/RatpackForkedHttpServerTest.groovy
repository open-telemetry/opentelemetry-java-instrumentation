/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import ratpack.server.RatpackServerSpec

class RatpackForkedHttpServerTest extends AbstractRatpackForkedHttpServerTest implements LibraryTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
    RatpackTracing tracing = RatpackTracing.builder(openTelemetry)
      .setCapturedServerRequestHeaders([AbstractHttpServerTest.TEST_REQUEST_HEADER])
      .setCapturedServerResponseHeaders([AbstractHttpServerTest.TEST_RESPONSE_HEADER])
      .build()
    serverSpec.registryOf {
      tracing.configureServerRegistry(it)
    }
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    false
  }
}
