/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.protocol.RedisCommand;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceBatchRequest {
  private final String operationName;
  @Nullable private final Long batchSize;
  @Nullable private final InetSocketAddress serverAddress;
  @Nullable private final RedisURI redisUri;
  @Nullable private final Integer database;

  private LettuceBatchRequest(
      String operationName,
      @Nullable Long batchSize,
      @Nullable InetSocketAddress serverAddress,
      @Nullable RedisURI redisUri,
      @Nullable Integer database) {
    this.operationName = operationName;
    this.batchSize = batchSize;
    this.serverAddress = serverAddress;
    this.redisUri = redisUri;
    this.database = database;
  }

  static LettuceBatchRequest create(
      List<RedisCommand<?, ?, ?>> commands,
      @Nullable InetSocketAddress serverAddress,
      @Nullable RedisURI redisUri,
      @Nullable Integer database) {
    return new LettuceBatchRequest(
        operationName(commands),
        commands.size() != 1 ? (long) commands.size() : null,
        serverAddress,
        redisUri,
        database);
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
  RedisURI getRedisUri() {
    return redisUri;
  }

  @Nullable
  Integer getDatabase() {
    return database;
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
