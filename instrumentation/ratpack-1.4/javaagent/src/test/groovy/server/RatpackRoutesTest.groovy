/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackRoutesTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import ratpack.server.RatpackServerSpec

class RatpackRoutesTest extends AbstractRatpackRoutesTest implements AgentTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    return [
      SemanticAttributes.NET_PEER_NAME
    ]
  }
}
