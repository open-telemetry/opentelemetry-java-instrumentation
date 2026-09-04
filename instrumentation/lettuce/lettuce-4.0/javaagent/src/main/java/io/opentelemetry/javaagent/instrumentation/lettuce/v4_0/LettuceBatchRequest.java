/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceBatchRequest {
  private final String operationName;
  @Nullable private final Long batchSize;
  private final List<RedisCommand<?, ?, ?>> commands;
  @Nullable private final InetSocketAddress serverAddress;
  @Nullable private final Integer databaseIndex;
  @Nullable private final RedisServerTarget serverTarget;

  private LettuceBatchRequest(
      String operationName,
      @Nullable Long batchSize,
      List<RedisCommand<?, ?, ?>> commands,
      @Nullable InetSocketAddress serverAddress,
      @Nullable Integer databaseIndex,
      @Nullable RedisServerTarget serverTarget) {
    this.operationName = operationName;
    this.batchSize = batchSize;
    this.commands = commands;
    this.serverAddress = serverAddress;
    this.databaseIndex = databaseIndex;
    this.serverTarget = serverTarget;
  }

  static LettuceBatchRequest create(
      List<RedisCommand<?, ?, ?>> commands,
      @Nullable InetSocketAddress serverAddress,
      @Nullable Integer databaseIndex,
      @Nullable RedisServerTarget serverTarget) {
    return new LettuceBatchRequest(
        operationName(commands),
        commands.size() != 1 ? (long) commands.size() : null,
        commands,
        serverAddress,
        databaseIndex,
        serverTarget);
  }

  String getOperationName() {
    return operationName;
  }

  @Nullable
  Long getBatchSize() {
    return batchSize;
  }

  @Nullable
  InetSocketAddress getServerAddress() {
    return serverAddress;
  }

  @Nullable
  SocketAddress getPeerAddress() {
    // Read when the span ends so an outbound write after the flush can still supply the peer.
    return LettuceSingletons.batchPeerAddress(commands);
  }

  @Nullable
  Integer getDatabaseIndex() {
    return databaseIndex;
  }

  @Nullable
  RedisServerTarget getServerTarget() {
    return serverTarget;
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
