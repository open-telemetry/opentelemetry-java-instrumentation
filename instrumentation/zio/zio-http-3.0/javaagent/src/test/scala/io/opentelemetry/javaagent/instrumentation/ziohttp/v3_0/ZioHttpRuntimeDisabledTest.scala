/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import org.junit.jupiter.api.Test

class ZioHttpRuntimeDisabledTest extends AbstractZioHttpTest {

  @Test
  def synchronousRouteWithoutRuntimePropagation(): Unit = {
    assertRoute("greet")
  }

  @Test
  def yieldingRouteWithoutRuntimePropagation(): Unit = {
    assertRoute("greet-async")
  }
}
