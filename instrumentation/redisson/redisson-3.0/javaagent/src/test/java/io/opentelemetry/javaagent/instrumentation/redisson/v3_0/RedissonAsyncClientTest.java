/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.AbstractRedissonAsyncClientTest;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

class RedissonAsyncClientTest extends AbstractRedissonAsyncClientTest {
  @Override
  protected RBatch createBatch(RedissonClient redisson) {
    return redisson.createBatch();
  }
}
