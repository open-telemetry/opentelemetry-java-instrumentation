/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import redis.Operation;
import redis.RedisCommand;
import scala.collection.Iterator;
import scala.collection.immutable.Queue;

class RediscalaRequest {

  // Command classes that rediscala names after something other than the command it sends, either
  // because the name also covers an option, e.g. ZrangeWithscores sends ZRANGE ... WITHSCORES, or
  // because the class name does not start with the container command, e.g. SenMasters sends
  // SENTINEL MASTERS.
  private static final Map<String, String> COMMAND_NAMES = commandNames();

  // Redis commands that take a subcommand, e.g. CONFIG GET. Rediscala names the command class
  // after both tokens, e.g. ConfigGet.
  private static final List<String> CONTAINER_COMMANDS =
      asList(
          "ACL", "CLIENT", "CLUSTER", "COMMAND", "CONFIG", "DEBUG", "LATENCY", "MEMORY", "OBJECT",
          "PUBSUB", "SCRIPT", "SLOWLOG", "XGROUP", "XINFO");

  private final String operationName;
  private final String stableOperationName;
  @Nullable private final Long batchSize;
  @Nullable private final ServerEndpoint endpoint;
  @Nullable private final RedisServerTarget serverTarget;

  static RediscalaRequest create(
      RedisCommand<?, ?> command,
      @Nullable ServerEndpoint endpoint,
      @Nullable RedisServerTarget serverTarget) {
    return new RediscalaRequest(
        operationName(command, /* stable= */ false),
        operationName(command, /* stable= */ true),
        null,
        endpoint,
        serverTarget);
  }

  static RediscalaRequest createTransaction(
      Queue<Operation<?, ?>> operations,
      @Nullable ServerEndpoint endpoint,
      @Nullable RedisServerTarget serverTarget) {
    return new RediscalaRequest(
        transactionOperationName(operations, /* stable= */ false),
        transactionOperationName(operations, /* stable= */ true),
        batchSize(operations),
        endpoint,
        serverTarget);
  }

  private RediscalaRequest(
      String operationName,
      String stableOperationName,
      @Nullable Long batchSize,
      @Nullable ServerEndpoint endpoint,
      @Nullable RedisServerTarget serverTarget) {
    this.operationName = operationName;
    this.stableOperationName = stableOperationName;
    this.batchSize = batchSize;
    this.endpoint = endpoint;
    this.serverTarget = serverTarget;
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
    return endpoint != null ? endpoint.getHost() : null;
  }

  @Nullable
  Integer getPort() {
    return endpoint != null ? endpoint.getPort() : null;
  }

  @Nullable
  Integer getDatabaseIndex() {
    return endpoint != null ? endpoint.getDatabaseIndex() : null;
  }

  @Nullable
  RedisServerTarget getServerTarget() {
    return serverTarget;
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
    return stable ? stableOperationName(name) : name;
  }

  private static String stableOperationName(String className) {
    // commands without arguments are scala objects, whose class name ends with $
    String name =
        className.endsWith("$") ? className.substring(0, className.length() - 1) : className;

    String commandName = COMMAND_NAMES.get(name);
    if (commandName != null) {
      return commandName;
    }
    return containerCommand(name);
  }

  private static String containerCommand(String name) {
    for (String container : CONTAINER_COMMANDS) {
      if (name.length() > container.length() && name.startsWith(container)) {
        return container;
      }
    }
    return name;
  }

  private static Map<String, String> commandNames() {
    Map<String, String> names = new HashMap<>();
    names.put("BITCOUNTRANGE", "BITCOUNT");
    names.put("EXISTSMANY", "EXISTS");
    names.put("GEORADIUSBYMEMBERWITHOPT", "GEORADIUSBYMEMBER");
    names.put("RENAMEX", "RENAMENX");
    names.put("SENGETMASTERADDR", "SENTINEL");
    names.put("SENMASTERFAILOVER", "SENTINEL");
    names.put("SENMASTERINFO", "SENTINEL");
    names.put("SENMASTERS", "SENTINEL");
    names.put("SENRESETMASTER", "SENTINEL");
    names.put("SENSLAVES", "SENTINEL");
    names.put("SLAVEOFNOONE", "SLAVEOF");
    names.put("SORTSTORE", "SORT");
    names.put("SRANDMEMBERS", "SRANDMEMBER");
    names.put("ZINTERSTOREWEIGHTED", "ZINTERSTORE");
    names.put("ZRANGEBYSCOREWITHSCORES", "ZRANGEBYSCORE");
    names.put("ZRANGEWITHSCORES", "ZRANGE");
    names.put("ZREVRANGEBYSCOREWITHSCORES", "ZREVRANGEBYSCORE");
    names.put("ZREVRANGEWITHSCORES", "ZREVRANGE");
    names.put("ZUNIONSTOREWEIGHTED", "ZUNIONSTORE");
    return names;
  }
}
