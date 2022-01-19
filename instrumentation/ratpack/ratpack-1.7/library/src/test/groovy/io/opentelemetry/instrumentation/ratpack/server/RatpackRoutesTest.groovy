/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server


import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import ratpack.server.RatpackServerSpec

class RatpackRoutesTest extends AbstractRatpackRoutesTest implements LibraryTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
    RatpackTracing tracing = RatpackTracing.create(openTelemetry)
    serverSpec.registryOf {
      tracing.configureServerRegistry(it)
    }
  }

  @Override
  boolean hasHandlerSpan() {
    return false
  }
}
