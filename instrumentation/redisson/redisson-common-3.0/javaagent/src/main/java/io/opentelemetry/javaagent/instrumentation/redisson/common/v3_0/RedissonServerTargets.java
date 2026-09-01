/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.connection.MasterSlaveConnectionManager;

public class RedissonServerTargets {

  private static final VirtualField<MasterSlaveConnectionManager, RedisServerTarget>
      MANAGER_TARGET =
          VirtualField.find(MasterSlaveConnectionManager.class, RedisServerTarget.class);

  private static final VirtualField<RedisClient, RedisServerTarget> CLIENT_TARGET =
      VirtualField.find(RedisClient.class, RedisServerTarget.class);

  public static void setManagerTarget(
      MasterSlaveConnectionManager manager, @Nullable RedisServerTarget target) {
    MANAGER_TARGET.set(manager, target);
  }

  public static void attachClientTarget(
      MasterSlaveConnectionManager manager, @Nullable RedisClient client) {
    RedisServerTarget target = MANAGER_TARGET.get(manager);
    if (client != null && target != null) {
      CLIENT_TARGET.set(client, target);
    }
  }

  @Nullable
  public static RedisServerTarget of(RedisConnection connection) {
    RedisClient client = connection.getRedisClient();
    return client != null ? CLIENT_TARGET.get(client) : null;
  }

  private RedissonServerTargets() {}
}
