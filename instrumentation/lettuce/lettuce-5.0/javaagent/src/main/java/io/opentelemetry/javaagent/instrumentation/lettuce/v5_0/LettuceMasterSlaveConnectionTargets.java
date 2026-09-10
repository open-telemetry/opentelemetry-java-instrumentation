/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_TARGET;

import io.lettuce.core.RedisChannelHandler;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import javax.annotation.Nullable;

public final class LettuceMasterSlaveConnectionTargets {

  public static void setTarget(@Nullable Object connection, RedisServerTarget target) {
    if (!(connection instanceof RedisChannelHandler)) {
      return;
    }
    RedisChannelHandler<?, ?> connectionHandler = (RedisChannelHandler<?, ?>) connection;

    RedisServerTarget currentTarget = CONNECTION_TARGET.get(connectionHandler);
    if (currentTarget == null || currentTarget.getPort() != null || target.getPort() == null) {
      CONNECTION_TARGET.set(connectionHandler, target);
    }
  }

  private LettuceMasterSlaveConnectionTargets() {}
}
