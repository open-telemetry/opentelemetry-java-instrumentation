/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import org.junit.jupiter.api.{Disabled, Test}

class ZioHttpRuntimeDisabledTest extends AbstractZioHttpTest {

  @Test
  def synchronousRouteWithoutRuntimePropagation(): Unit = {
    assertRoute("greet")
  }

  @Test
  @Disabled(
    "HTTP route is lost across suspension without runtime propagation: https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/14938"
  )
  def yieldingRouteWithoutRuntimePropagation(): Unit = {
    assertRoute("greet-async")
  }
}
