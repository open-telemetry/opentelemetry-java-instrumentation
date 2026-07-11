/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import java.net.InetSocketAddress;
import java.util.Locale;
import javax.annotation.Nullable;
import redis.Operation;
import redis.RedisCommand;
import scala.collection.Iterator;
import scala.collection.immutable.Queue;

class RediscalaRequest {
  private final String operationName;
  @Nullable private final Long batchSize;
  @Nullable private final InetSocketAddress address;

  static RediscalaRequest create(RedisCommand<?, ?> command, @Nullable InetSocketAddress address) {
    return new RediscalaRequest(operationName(command), null, address);
  }

  static RediscalaRequest createTransaction(
      Queue<Operation<?, ?>> operations, @Nullable InetSocketAddress address) {
    return new RediscalaRequest(
        transactionOperationName(operations), batchSize(operations), address);
  }

  private RediscalaRequest(
      String operationName, @Nullable Long batchSize, @Nullable InetSocketAddress address) {
    this.operationName = operationName;
    this.batchSize = batchSize;
    this.address = address;
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

  private static String transactionOperationName(Queue<Operation<?, ?>> operations) {
    if (operations.isEmpty()) {
      return "MULTI";
    }

    Iterator<Operation<?, ?>> iterator = operations.iterator();
    String operationName = operationName(iterator.next().redisCommand());
    while (iterator.hasNext()) {
      if (!operationName.equals(operationName(iterator.next().redisCommand()))) {
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

  private static String operationName(RedisCommand<?, ?> command) {
    return command.getClass().getSimpleName().toUpperCase(Locale.ROOT);
  }
}
