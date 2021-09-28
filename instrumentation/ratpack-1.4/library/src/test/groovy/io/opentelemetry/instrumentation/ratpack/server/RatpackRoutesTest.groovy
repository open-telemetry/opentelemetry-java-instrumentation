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

  @Override
  List<AttributeKey<?>> extraAttributes() {
    return [
      SemanticAttributes.HTTP_ROUTE,
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_HOST,
      SemanticAttributes.HTTP_TARGET,
      SemanticAttributes.NET_TRANSPORT,
    ]
  }
}
