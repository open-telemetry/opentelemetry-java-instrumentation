/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1

import io.lettuce.core.RedisClient
import io.opentelemetry.instrumentation.lettuce.v5_1.AbstractLettuceSyncClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class LettuceSyncClientTest extends AbstractLettuceSyncClientTest implements AgentTestTrait {
  @Override
  RedisClient createClient(String uri) {
    return RedisClient.create(uri)
  }
}
