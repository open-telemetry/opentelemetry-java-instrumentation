/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import io.opentelemetry.javaagent.instrumentation.redisson.AbstractRedissonClientTest;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

public class RedissonClientTest extends AbstractRedissonClientTest {
  @Override
  protected RBatch createBatch(RedissonClient redisson) {
    return redisson.createBatch();
  }
}
