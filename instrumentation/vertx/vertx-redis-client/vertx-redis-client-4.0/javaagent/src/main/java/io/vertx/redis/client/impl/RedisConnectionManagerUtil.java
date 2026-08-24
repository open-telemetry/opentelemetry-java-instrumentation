/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client.impl;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

/**
 * Keeps the target a client was configured with on its connection manager.
 *
 * <p>{@code RedisConnectionManager} is not visible outside of this package, so callers pass the
 * manager in as an {@link Object}.
 */
public class RedisConnectionManagerUtil {

  private static final VirtualField<RedisConnectionManager, RedisServerTarget> targetField =
      VirtualField.find(RedisConnectionManager.class, RedisServerTarget.class);

  public static void setServerTarget(Object manager, @Nullable RedisServerTarget target) {
    targetField.set((RedisConnectionManager) manager, target);
  }

  @Nullable
  public static RedisServerTarget getServerTarget(Object manager) {
    return targetField.get((RedisConnectionManager) manager);
  }

  private RedisConnectionManagerUtil() {}
}
