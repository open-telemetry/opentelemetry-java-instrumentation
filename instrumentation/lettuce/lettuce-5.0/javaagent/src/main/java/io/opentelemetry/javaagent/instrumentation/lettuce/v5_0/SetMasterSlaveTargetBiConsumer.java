/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_TARGET;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.MASTER_SLAVE_CONNECTION_DELEGATE;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.function.BiConsumer;

public class SetMasterSlaveTargetBiConsumer implements BiConsumer<Object, Throwable> {

  private final RedisServerTarget target;

  public SetMasterSlaveTargetBiConsumer(RedisServerTarget target) {
    this.target = target;
  }

  @Override
  public void accept(Object connection, Throwable throwable) {
    setTarget(connection, target);
  }

  public static void setTarget(Object connection, RedisServerTarget target) {
    RedisChannelHandler<?, ?> connectionHandler = null;
    if (connection instanceof RedisChannelHandler) {
      connectionHandler = (RedisChannelHandler<?, ?>) connection;
    } else if (connection instanceof StatefulRedisMasterSlaveConnection) {
      connectionHandler =
          MASTER_SLAVE_CONNECTION_DELEGATE.get(
              (StatefulRedisMasterSlaveConnection<?, ?>) connection);
    }
    if (connectionHandler == null) {
      return;
    }

    RedisServerTarget currentTarget = CONNECTION_TARGET.get(connectionHandler);
    // A nested connection callback must not replace a configured list or logical target.
    if (currentTarget == null || currentTarget.getPort() != null || target.getPort() == null) {
      CONNECTION_TARGET.set(connectionHandler, target);
    }
  }
}
