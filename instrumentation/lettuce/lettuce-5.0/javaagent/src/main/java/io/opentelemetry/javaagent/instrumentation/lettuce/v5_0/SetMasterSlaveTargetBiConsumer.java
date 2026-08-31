/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_TARGET;

import io.lettuce.core.RedisChannelHandler;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.function.BiConsumer;

public class SetMasterSlaveTargetBiConsumer implements BiConsumer<Object, Throwable> {

  private final RedisServerTarget target;

  public SetMasterSlaveTargetBiConsumer(RedisServerTarget target) {
    this.target = target;
  }

  @Override
  public void accept(Object connection, Throwable throwable) {
    if (connection instanceof RedisChannelHandler) {
      CONNECTION_TARGET.set((RedisChannelHandler<?, ?>) connection, target);
    }
  }
}
