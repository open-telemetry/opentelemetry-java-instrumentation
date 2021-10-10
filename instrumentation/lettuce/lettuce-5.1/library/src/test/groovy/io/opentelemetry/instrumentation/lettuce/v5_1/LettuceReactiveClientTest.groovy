/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1

import io.lettuce.core.RedisClient
import io.lettuce.core.resource.ClientResources
import io.opentelemetry.instrumentation.reactor.ContextPropagationReactorOperator
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import spock.lang.Shared

class LettuceReactiveClientTest extends AbstractLettuceReactiveClientTest implements LibraryTestTrait {
  @Shared
  ContextPropagationReactorOperator tracingOperator = ContextPropagationReactorOperator.create()

  @Override
  RedisClient createClient(String uri) {
    return RedisClient.create(
      ClientResources.builder()
        .tracing(LettuceTracing.create(getOpenTelemetry()).newTracing())
        .build(),
      uri)
  }

  def setupSpec() {
    tracingOperator.registerOnEachOperator()
  }

  def cleanupSpec() {
    tracingOperator.resetOnEachOperator()
  }
}
