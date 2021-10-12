/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import ratpack.server.RatpackServerSpec

class RatpackHttpServerTest extends AbstractRatpackHttpServerTest implements LibraryTestTrait {
  @Override
  void configure(RatpackServerSpec serverSpec) {
    RatpackTracing tracing = RatpackTracing.newBuilder(openTelemetry)
      .captureHttpHeaders(capturedHttpHeadersForTesting())
      .build()
    serverSpec.registryOf {
      tracing.configureServerRegistry(it)
    }
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    false
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    return [
      SemanticAttributes.HTTP_ROUTE,
      SemanticAttributes.NET_TRANSPORT
    ]
  }
}
