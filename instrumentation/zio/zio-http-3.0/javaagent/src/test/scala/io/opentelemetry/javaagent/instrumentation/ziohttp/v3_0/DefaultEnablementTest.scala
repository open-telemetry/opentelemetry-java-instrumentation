/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import org.junit.jupiter.api.Test

class DefaultEnablementTest extends AbstractZioHttpTest {

  @Test
  def defaultEnablement(): Unit = {
    assertRoute(
      "greet-async",
      java.lang.Boolean.getBoolean("otel.instrumentation.zio-http.enabled")
    )
  }
}
