/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackRoutesTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import ratpack.server.RatpackServerSpec

class RatpackRoutesTest extends AbstractRatpackRoutesTest implements AgentTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }
}
