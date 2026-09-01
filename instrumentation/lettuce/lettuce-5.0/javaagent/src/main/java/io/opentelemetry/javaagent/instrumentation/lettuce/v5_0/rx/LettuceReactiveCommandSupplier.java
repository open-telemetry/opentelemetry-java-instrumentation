/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons;
import java.util.function.Supplier;

public class LettuceReactiveCommandSupplier<K, V, T> implements Supplier<RedisCommand<K, V, T>> {

  private final Supplier<RedisCommand<K, V, T>> delegate;
  private final StatefulConnection<?, ?> connection;
  private final RedisCommand<K, V, T> tracingCommand;
  private boolean first = true;

  public LettuceReactiveCommandSupplier(
      Supplier<RedisCommand<K, V, T>> delegate, StatefulConnection<?, ?> connection) {
    this.delegate = delegate;
    this.connection = connection;
    tracingCommand = delegate.get();
    LettuceSingletons.attachAddress(tracingCommand, connection);
  }

  @Override
  public synchronized RedisCommand<K, V, T> get() {
    RedisCommand<K, V, T> command;
    if (first) {
      first = false;
      command = tracingCommand;
    } else {
      command = delegate.get();
      LettuceSingletons.attachAddress(command, connection);
    }
    LettuceSingletons.linkCommandPeer(command, tracingCommand);
    return command;
  }

  public RedisCommand<K, V, T> getTracingCommand() {
    return tracingCommand;
  }
}
