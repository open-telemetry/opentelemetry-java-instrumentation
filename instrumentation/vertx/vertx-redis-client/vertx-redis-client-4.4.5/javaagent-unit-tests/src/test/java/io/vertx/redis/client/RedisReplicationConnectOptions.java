/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client;

public class RedisReplicationConnectOptions extends RedisConnectOptions {

  private final TestTopology topology;

  public RedisReplicationConnectOptions(TestTopology topology) {
    this.topology = topology;
  }

  public TestTopology getTopology() {
    return topology;
  }

  @Override
  public RedisReplicationConnectOptions addConnectionString(String connectionString) {
    super.addConnectionString(connectionString);
    return this;
  }

  public enum TestTopology {
    STATIC,
    DISCOVER;

    @Override
    public String toString() {
      return "custom";
    }
  }
}
