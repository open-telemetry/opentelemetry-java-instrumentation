/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client.impl;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

public class RedisConnectionManagerUtil {

  private static final VirtualField<RedisConnectionManager, RedisServerTarget> TARGET_FIELD =
      VirtualField.find(RedisConnectionManager.class, RedisServerTarget.class);
  private static final ScopedThreadValue<RedisServerTarget> currentServerTarget =
      new ScopedThreadValue<>();

  public static void setServerTarget(Object manager, @Nullable RedisServerTarget target) {
    TARGET_FIELD.set((RedisConnectionManager) manager, target);
  }

  public static ScopedThreadValue<RedisServerTarget> currentServerTarget() {
    return currentServerTarget;
  }

  @Nullable
  public static RedisServerTarget getServerTarget(Object manager) {
    return TARGET_FIELD.get((RedisConnectionManager) manager);
  }

  private RedisConnectionManagerUtil() {}
}
