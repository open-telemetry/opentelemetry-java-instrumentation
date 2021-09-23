/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1

import io.lettuce.core.RedisClient
import io.lettuce.core.resource.ClientResources
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class LettuceAsyncClientTest extends AbstractLettuceAsyncClientTest implements LibraryTestTrait {
  @Override
  RedisClient createClient(String uri) {
    return RedisClient.create(
      ClientResources.builder()
        .tracing(LettuceTracing.create(getOpenTelemetry()).newTracing())
        .build(),
      uri)
  }

  @Override
  boolean testCallback() {
    // context is not propagated into callbacks
    return false
  }
}
