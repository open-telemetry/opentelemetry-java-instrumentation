/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server


import io.opentelemetry.instrumentation.ratpack.RatpackTelemetry
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import ratpack.server.RatpackServerSpec

class RatpackRoutesTest extends AbstractRatpackRoutesTest implements LibraryTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
    RatpackTelemetry telemetry = RatpackTelemetry.create(openTelemetry)
    serverSpec.registryOf {
      telemetry.configureServerRegistry(it)
    }
  }

  @Override
  boolean hasHandlerSpan() {
    return false
  }
}
