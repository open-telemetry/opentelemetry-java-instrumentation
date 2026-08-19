/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.Arrays.asList;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import redis.Operation;
import redis.RedisCommand;
import scala.collection.Iterator;
import scala.collection.immutable.Queue;

class RediscalaRequest {

  // Redis commands that are split into a container command and a subcommand, e.g. CONFIG GET.
  // Rediscala names the command class after both tokens, e.g. ConfigGet.
  private static final List<String> CONTAINER_COMMANDS =
      asList(
          "ACL", "CLIENT", "CLUSTER", "COMMAND", "CONFIG", "DEBUG", "LATENCY", "MEMORY", "OBJECT",
          "PUBSUB", "SCRIPT", "SLOWLOG", "XGROUP", "XINFO");

  private final String operationName;
  private final String stableOperationName;
  @Nullable private final Long batchSize;
  @Nullable private final String host;
  @Nullable private final Integer port;

  static RediscalaRequest create(
      RedisCommand<?, ?> command, @Nullable String host, @Nullable Integer port) {
    return new RediscalaRequest(
        operationName(command, /* stable= */ false),
        operationName(command, /* stable= */ true),
        null,
        host,
        port);
  }

  static RediscalaRequest createTransaction(
      Queue<Operation<?, ?>> operations, @Nullable String host, @Nullable Integer port) {
    return new RediscalaRequest(
        transactionOperationName(operations, /* stable= */ false),
        transactionOperationName(operations, /* stable= */ true),
        batchSize(operations),
        host,
        port);
  }

  private RediscalaRequest(
      String operationName,
      String stableOperationName,
      @Nullable Long batchSize,
      @Nullable String host,
      @Nullable Integer port) {
    this.operationName = operationName;
    this.stableOperationName = stableOperationName;
    this.batchSize = batchSize;
    this.host = host;
    this.port = port;
  }

  String getOperationName() {
    return operationName;
  }

  String getStableOperationName() {
    return stableOperationName;
  }

  @Nullable
  Long getBatchSize() {
    return batchSize;
  }

  @Nullable
  String getHost() {
    return host;
  }

  @Nullable
  Integer getPort() {
    return port;
  }

  private static String transactionOperationName(
      Queue<Operation<?, ?>> operations, boolean stable) {
    if (operations.isEmpty()) {
      return "MULTI";
    }

    Iterator<Operation<?, ?>> iterator = operations.iterator();
    String operationName = operationName(iterator.next().redisCommand(), stable);
    while (iterator.hasNext()) {
      if (!operationName.equals(operationName(iterator.next().redisCommand(), stable))) {
        return "MULTI";
      }
    }
    return "MULTI " + operationName;
  }

  @Nullable
  private static Long batchSize(Queue<Operation<?, ?>> operations) {
    int size = operations.size();
    return size != 1 ? (long) size : null;
  }

  private static String operationName(RedisCommand<?, ?> command, boolean stable) {
    String name = command.getClass().getSimpleName().toUpperCase(Locale.ROOT);
    return stable ? containerCommand(name) : name;
  }

  private static String containerCommand(String name) {
    for (String container : CONTAINER_COMMANDS) {
      if (name.length() > container.length() && name.startsWith(container)) {
        return container;
      }
    }
    return name;
  }
}
