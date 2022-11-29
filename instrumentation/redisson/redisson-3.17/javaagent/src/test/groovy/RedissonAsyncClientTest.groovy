/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class RedissonAsyncClientTest extends AbstractRedissonAsyncClientTest {

  @Override
  boolean useRedisProtocol() {
    true
  }
}
