/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import io.lettuce.core.protocol.RedisCommand;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

public final class LettuceReactiveCommandContext {
  private static final ThreadLocal<Deque<RedisCommand<?, ?, ?>>> reactiveCommands =
      new ThreadLocal<>();

  public static void enter(RedisCommand<?, ?, ?> command) {
    Deque<RedisCommand<?, ?, ?>> commands = reactiveCommands.get();
    if (commands == null) {
      commands = new ArrayDeque<>();
      reactiveCommands.set(commands);
    }
    commands.push(command);
  }

  public static void exit() {
    Deque<RedisCommand<?, ?, ?>> commands = reactiveCommands.get();
    if (commands == null) {
      return;
    }
    commands.poll();
    if (commands.isEmpty()) {
      reactiveCommands.remove();
    }
  }

  @Nullable
  public static RedisCommand<?, ?, ?> current() {
    Deque<RedisCommand<?, ?, ?>> commands = reactiveCommands.get();
    return commands == null ? null : commands.peek();
  }

  private LettuceReactiveCommandContext() {}
}
