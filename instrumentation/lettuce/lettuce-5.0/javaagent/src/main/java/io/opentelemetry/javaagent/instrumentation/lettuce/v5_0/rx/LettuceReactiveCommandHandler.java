/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import io.lettuce.core.protocol.RedisCommand;

public interface LettuceReactiveCommandHandler {
  void onCommand(RedisCommand<?, ?, ?> command);

  default void onCancel() {}
}
