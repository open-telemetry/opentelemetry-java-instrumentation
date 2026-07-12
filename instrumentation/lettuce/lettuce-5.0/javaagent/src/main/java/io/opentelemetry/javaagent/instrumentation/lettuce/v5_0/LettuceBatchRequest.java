/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.emptyList;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceBatchRequest {
  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "lettuce"));
  private static final int LIMIT = 32 * 1024;

  private final String operationName;
  @Nullable private final String queryText;
  @Nullable private final Long batchSize;
  @Nullable private final ServerEndpoint serverEndpoint;

  private LettuceBatchRequest(
      String operationName,
      @Nullable String queryText,
      @Nullable Long batchSize,
      @Nullable ServerEndpoint serverEndpoint) {
    this.operationName = operationName;
    this.queryText = queryText;
    this.batchSize = batchSize;
    this.serverEndpoint = serverEndpoint;
  }

  static LettuceBatchRequest create(List<RedisCommand<?, ?, ?>> commands) {
    return new LettuceBatchRequest(
        operationName(commands),
        queryText(commands),
        commands.size() != 1 ? (long) commands.size() : null,
        commands.isEmpty() ? null : LettuceSingletons.COMMAND_ADDRESS.get(commands.get(0)));
  }

  String getOperationName() {
    return operationName;
  }

  @Nullable
  String getQueryText() {
    return queryText;
  }

  @Nullable
  Long getBatchSize() {
    return batchSize;
  }

  @Nullable
  ServerEndpoint getServerEndpoint() {
    return serverEndpoint;
  }

  private static String operationName(List<RedisCommand<?, ?, ?>> commands) {
    if (commands.size() == 1) {
      return LettuceInstrumentationUtil.getCommandName(commands.get(0));
    }
    String operationName = LettuceInstrumentationUtil.getCommandName(commands.get(0));
    for (int i = 1; i < commands.size(); i++) {
      if (!operationName.equals(LettuceInstrumentationUtil.getCommandName(commands.get(i)))) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + operationName;
  }

  @Nullable
  private static String queryText(List<RedisCommand<?, ?, ?>> commands) {
    if (commands.isEmpty()) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    for (RedisCommand<?, ?, ?> command : commands) {
      String commandQueryText = queryText(command);
      String separator = builder.length() == 0 ? "" : batchQuerySeparator();
      if (builder.length() + separator.length() + commandQueryText.length() > LIMIT) {
        break;
      }
      builder.append(separator).append(commandQueryText);
    }
    return builder.toString();
  }

  private static String queryText(RedisCommand<?, ?, ?> command) {
    String commandName = LettuceInstrumentationUtil.getCommandName(command);
    List<String> args =
        command.getArgs() == null
            ? emptyList()
            : LettuceArgSplitter.splitArgs(command.getArgs().toCommandString());
    return sanitizer.sanitize(commandName, args);
  }

  private static String batchQuerySeparator() {
    return emitStableDatabaseSemconv() ? "; " : ";";
  }
}
