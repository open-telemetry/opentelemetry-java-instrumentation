/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.util.Collections.emptyList;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import javax.annotation.Nullable;

final class LettuceBatchRequest {
  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "lettuce"));

  private final String operationName;
  @Nullable private final String queryText;
  @Nullable private final Long batchSize;

  private LettuceBatchRequest(
      String operationName, @Nullable String queryText, @Nullable Long batchSize) {
    this.operationName = operationName;
    this.queryText = queryText;
    this.batchSize = batchSize;
  }

  static LettuceBatchRequest create(List<RedisCommand<?, ?, ?>> commands) {
    return new LettuceBatchRequest(
        operationName(commands),
        queryText(commands),
        commands.size() > 1 ? (long) commands.size() : null);
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
    StringJoiner joiner = new StringJoiner(";");
    List<String> queryTexts = new ArrayList<>(commands.size());
    for (RedisCommand<?, ?, ?> command : commands) {
      queryTexts.add(queryText(command));
    }
    if (queryTexts.isEmpty()) {
      return null;
    }
    for (String queryText : queryTexts) {
      joiner.add(queryText);
    }
    return joiner.toString();
  }

  private static String queryText(RedisCommand<?, ?, ?> command) {
    String commandName = LettuceInstrumentationUtil.getCommandName(command);
    List<String> args =
        command.getArgs() == null
            ? emptyList()
            : LettuceArgSplitter.splitArgs(command.getArgs().toCommandString());
    return sanitizer.sanitize(commandName, args);
  }
}
