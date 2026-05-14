/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.opentelemetry.javaagent.instrumentation.redisson.AbstractRedissonClientTest;

class RedissonClientTest extends AbstractRedissonClientTest {
  @Override
  protected boolean useRedisProtocol() {
    return true;
  }

  @Override
  protected boolean lockHas3Traces() {
    return testLatestDeps();
  }
}
