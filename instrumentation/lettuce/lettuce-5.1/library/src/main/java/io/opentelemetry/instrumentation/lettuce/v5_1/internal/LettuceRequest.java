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
  @Nullable private String command;
  @Nullable private List<String> argsList;
  @Nullable private String argsString;
  @Nullable private InetSocketAddress address;
  @Nullable private Long databaseIndex;
  @Nullable private Long batchSize;
  private boolean pipeline;

  LettuceRequest(RedisCommandSanitizer sanitizer) {
    this.sanitizer = sanitizer;
  }

  void setCommand(String command) {
    this.command = command;
  }

  @Nullable
  String getCommand() {
    return command;
  }

  void setArgsList(List<String> argsList) {
    this.argsList = argsList;
  }

  void setArgsString(String argsString) {
    this.argsString = argsString;
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

  private void setPipeline(List<RedisCommand<?, ?, ?>> commands) {
    command = pipelineOperationName(commands);
    argsList = null;
    argsString = pipelineStatement(commands);
    batchSize = commands.size() > 1 ? (long) commands.size() : null;
    pipeline = true;
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
    request.setPipeline(commands);
    request.address = address;
    request.databaseIndex = databaseIndex;
    return request;
  }

  @Nullable
  String getStatement() {
    String cmd = command;
    if (cmd == null) {
      return null;
    }
    if (pipeline) {
      return argsString;
    }
    List<String> args = argsList;
    if (args == null) {
      args = splitArgs(argsString);
    }
    return sanitizer.sanitize(cmd, args);
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
