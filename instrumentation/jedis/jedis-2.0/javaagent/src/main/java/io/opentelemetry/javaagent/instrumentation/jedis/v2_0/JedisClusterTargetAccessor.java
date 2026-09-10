/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Map;
import javax.annotation.Nullable;
import redis.clients.jedis.JedisClusterConnectionHandler;
import redis.clients.util.Pool;

public final class JedisClusterTargetAccessor {

  private static final VirtualField<JedisClusterConnectionHandler, ClusterTargetState>
      CLUSTER_TARGET_STATE = getClusterTargetStateVirtualField();

  @NoMuzzle
  public static void setTarget(
      JedisClusterConnectionHandler handler, @Nullable RedisServerTarget target) {
    CLUSTER_TARGET_STATE.set(handler, new ClusterTargetState(target));
  }

  @NoMuzzle
  public static void attachTarget(
      JedisClusterConnectionHandler handler, @Nullable Object connection) {
    ClusterTargetState state = CLUSTER_TARGET_STATE.get(handler);
    if (state != null) {
      JedisSingletons.attach(state.target, connection);
    }
  }

  @NoMuzzle
  public static void attachTargetToPools(
      JedisClusterConnectionHandler handler, @Nullable Map<?, ?> pools) {
    ClusterTargetState state = CLUSTER_TARGET_STATE.get(handler);
    if (state == null || pools == null) {
      return;
    }
    for (Object pool : pools.values()) {
      if (pool instanceof Pool<?>) {
        JedisSingletons.setPoolTarget((Pool<?>) pool, state.target);
      }
    }
  }

  @Nullable
  @NoMuzzle
  public static JedisSingletons.ConfiguredTargetScope openTargetScope(
      JedisClusterConnectionHandler handler) {
    ClusterTargetState state = CLUSTER_TARGET_STATE.get(handler);
    return state != null ? JedisSingletons.openConfiguredTargetScope(state.target) : null;
  }

  @NoMuzzle
  private static VirtualField<JedisClusterConnectionHandler, ClusterTargetState>
      getClusterTargetStateVirtualField() {
    return VirtualField.find(JedisClusterConnectionHandler.class, ClusterTargetState.class);
  }

  private JedisClusterTargetAccessor() {}

  static final class ClusterTargetState {
    @Nullable private final RedisServerTarget target;

    private ClusterTargetState(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }
}
