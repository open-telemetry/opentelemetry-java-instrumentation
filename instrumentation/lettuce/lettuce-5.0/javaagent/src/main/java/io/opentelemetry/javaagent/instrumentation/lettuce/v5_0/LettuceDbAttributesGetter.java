/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.REDIS;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter implements DbClientAttributesGetter<RedisCommand<?, ?, ?>> {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  @Override
  public String getDbSystem(RedisCommand<?, ?, ?> redisCommand) {
    return REDIS;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbQueryText(RedisCommand<?, ?, ?> request) {
    String command = LettuceInstrumentationUtil.getCommandName(request);
    List<String> args =
        request.getArgs() == null
            ? Collections.emptyList()
            : LettuceArgSplitter.splitArgs(request.getArgs().toCommandString());
    return sanitizer.sanitize(command, args);
  }

  @Nullable
  @Override
  public String getDbOperationName(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
