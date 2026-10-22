/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ZioHttpTest extends AbstractZioHttpTest {

  @ParameterizedTest
  @ValueSource(strings = Array("greet", "greet-async"))
  def spanName(route: String): Unit = {
    assertRoute(route)
  }
}
