/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1.internal;

import static io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter.splitArgs;
import static java.util.Collections.emptyList;

import io.lettuce.core.protocol.OtelCommandArgsUtil;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * Lettuce request data captured for telemetry.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LettuceRequest {

  private final RedisCommandSanitizer sanitizer;
  @Nullable private String operationName;
  @Nullable private String queryText;
  @Nullable private InetSocketAddress address;
  @Nullable private Long databaseIndex;
  @Nullable private Long batchSize;

  LettuceRequest(RedisCommandSanitizer sanitizer) {
    this.sanitizer = sanitizer;
  }

  void setCommand(String command) {
    operationName = command;
  }

  @Nullable
  String getCommand() {
    return operationName;
  }

  void setArgsList(List<String> argsList) {
    if (operationName != null) {
      queryText = sanitizer.sanitize(operationName, argsList);
    }
  }

  void setArgsString(String argsString) {
    if (operationName != null) {
      queryText = sanitizer.sanitize(operationName, splitArgs(argsString));
    }
  }

  void setAddress(InetSocketAddress address) {
    this.address = address;
  }

  @Nullable
  InetSocketAddress getAddress() {
    return address;
  }

  void setDatabaseIndex(long databaseIndex) {
    this.databaseIndex = databaseIndex;
  }

  @Nullable
  Long getDatabaseIndex() {
    return databaseIndex;
  }

  @Nullable
  Long getBatchSize() {
    return batchSize;
  }

  LettuceRequest copyAsPipeline(List<RedisCommand<?, ?, ?>> commands) {
    LettuceRequest request = new LettuceRequest(sanitizer);
    request.operationName = pipelineOperationName(commands);
    request.queryText = request.pipelineStatement(commands);
    request.batchSize = commands.size() != 1 ? (long) commands.size() : null;
    request.address = address;
    request.databaseIndex = databaseIndex;
    return request;
  }

  @Nullable
  String getStatement() {
    if (queryText != null) {
      return queryText;
    }
    // command with no args (no setArgs* call); sanitize on demand
    return operationName == null ? null : sanitizer.sanitize(operationName, emptyList());
  }

  private static String pipelineOperationName(List<RedisCommand<?, ?, ?>> commands) {
    String operationName = commands.get(0).getType().toString();
    if (commands.size() == 1) {
      return operationName;
    }
    for (int i = 1; i < commands.size(); i++) {
      if (!operationName.equals(commands.get(i).getType().toString())) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + operationName;
  }

  private String pipelineStatement(List<RedisCommand<?, ?, ?>> commands) {
    StringJoiner joiner = new StringJoiner(";");
    for (RedisCommand<?, ?, ?> command : commands) {
      String commandName = command.getType().toString();
      List<String> args =
          command.getArgs() == null
              ? emptyList()
              : OtelCommandArgsUtil.getCommandArgs(command.getArgs());
      joiner.add(sanitizer.sanitize(commandName, args));
    }
    return joiner.toString();
  }
}
