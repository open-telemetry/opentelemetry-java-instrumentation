/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import redis.clients.jedis.JedisClusterConnectionHandler;

public final class JedisClusterTargetAccessor {

  private static final VirtualField<JedisClusterConnectionHandler, RedisServerTarget>
      CLUSTER_TARGET = getClusterTargetVirtualField();

  public static void setTarget(
      JedisClusterConnectionHandler handler, @Nullable RedisServerTarget target) {
    CLUSTER_TARGET.set(handler, target);
  }

  @Nullable
  public static RedisServerTarget getTarget(JedisClusterConnectionHandler handler) {
    return CLUSTER_TARGET.get(handler);
  }

  private static VirtualField<JedisClusterConnectionHandler, RedisServerTarget>
      getClusterTargetVirtualField() {
    return VirtualField.find(JedisClusterConnectionHandler.class, RedisServerTarget.class);
  }

  private JedisClusterTargetAccessor() {}
}
