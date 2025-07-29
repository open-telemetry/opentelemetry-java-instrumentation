/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import io.opentelemetry.javaagent.instrumentation.redisson.AbstractRedissonAsyncClientTest;

class RedissonAsyncClientTest extends AbstractRedissonAsyncClientTest {

  @Override
  protected boolean useRedisProtocol() {
    return true;
  }
}
