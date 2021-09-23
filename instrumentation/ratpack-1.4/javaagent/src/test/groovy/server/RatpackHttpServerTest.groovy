/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server


import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackHttpServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import ratpack.server.RatpackServerSpec

class RatpackHttpServerTest extends AbstractRatpackHttpServerTest implements AgentTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
  }
}
