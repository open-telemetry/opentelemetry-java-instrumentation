/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackForkedHttpServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import ratpack.server.RatpackServerSpec

class RatpackForkedHttpServerTest extends AbstractRatpackForkedHttpServerTest implements AgentTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
  }

  @Override
  boolean testCapturedHttpHeaders() {
    false
  }
}
