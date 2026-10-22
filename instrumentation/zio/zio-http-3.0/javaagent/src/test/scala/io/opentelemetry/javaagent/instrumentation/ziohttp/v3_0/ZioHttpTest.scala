/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0

import org.junit.jupiter.api.Test

class ZioHttpTest extends AbstractZioHttpTest {
  @Test
  def delegatedRoute(): Unit = {
    assertDelegatedRoute()
  }

  @Test
  def deeplyDelegatedRoute(): Unit = {
    assertDeeplyDelegatedRoute()
  }

  @Test
  def failedRoute(): Unit = {
    assertFailedRoute()
  }
}
