/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1

import io.lettuce.core.RedisClient
import io.opentelemetry.instrumentation.lettuce.v5_1.AbstractLettuceAsyncClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class LettuceAsyncClientTest extends AbstractLettuceAsyncClientTest implements AgentTestTrait {
  @Override
  RedisClient createClient(String uri) {
    return RedisClient.create(uri)
  }

  @Override
  boolean connectHasSpans() {
    Boolean.getBoolean("testLatestDeps")
  }
}
