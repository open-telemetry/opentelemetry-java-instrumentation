/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import javax.annotation.Nullable;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisSocketFactory;

class JedisConnectionInfo {
  @Nullable private final RedisServerTarget serverTarget;
  @Nullable private final Long databaseIndex;

  private JedisConnectionInfo(
      @Nullable RedisServerTarget serverTarget, @Nullable Long databaseIndex) {
    this.serverTarget = serverTarget;
    this.databaseIndex = databaseIndex;
  }

  static JedisConnectionInfo create(
      @Nullable JedisSocketFactory socketFactory, @Nullable Object clientConfig) {
    // Without a client config, Jedis leaves the new Redis connection on the default database 0.
    Long databaseIndex =
        clientConfig instanceof JedisClientConfig
            ? Long.valueOf(((JedisClientConfig) clientConfig).getDatabase())
            : 0L;
    return new JedisConnectionInfo(
        JedisConfiguredTargets.socketFactoryTarget(socketFactory), databaseIndex);
  }

  @Nullable
  Long getDatabaseIndex() {
    return databaseIndex;
  }

  @Nullable
  RedisServerTarget getServerTarget() {
    return serverTarget;
  }
}
