/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceBatchRequest {
  private final String operationName;
  @Nullable private final Long batchSize;
  @Nullable private final InetSocketAddress address;

  private LettuceBatchRequest(
      String operationName, @Nullable Long batchSize, @Nullable InetSocketAddress address) {
    this.operationName = operationName;
    this.batchSize = batchSize;
    this.address = address;
  }

  static LettuceBatchRequest create(List<RedisCommand<?, ?, ?>> commands) {
    return new LettuceBatchRequest(
        operationName(commands),
        commands.size() != 1 ? (long) commands.size() : null,
        commands.isEmpty() ? null : LettuceSingletons.COMMAND_ADDRESS.get(commands.get(0)));
  }

  String getOperationName() {
    return operationName;
  }

  @Nullable
  Long getBatchSize() {
    return batchSize;
  }

  @Nullable
  InetSocketAddress getAddress() {
    return address;
  }

  private static String operationName(List<RedisCommand<?, ?, ?>> commands) {
    if (commands.size() == 1) {
      return commands.get(0).getType().name();
    }
    String operationName = commands.get(0).getType().name();
    for (int i = 1; i < commands.size(); i++) {
      if (!operationName.equals(commands.get(i).getType().name())) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + operationName;
  }
}
