/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import ratpack.server.RatpackServerSpec

class RatpackAsyncHttpServerTest extends AbstractRatpackAsyncHttpServerTest implements LibraryTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
    RatpackTracing tracing = RatpackTracing.builder(openTelemetry)
      .captureHttpServerHeaders(capturedHttpHeadersForTesting())
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
