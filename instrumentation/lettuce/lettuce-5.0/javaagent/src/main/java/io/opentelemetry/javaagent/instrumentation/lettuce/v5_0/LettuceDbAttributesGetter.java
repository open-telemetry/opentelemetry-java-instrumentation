/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter
    implements DbClientAttributesGetter<RedisCommand<?, ?, ?>, Void> {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DeclarativeConfigUtil.getBoolean(
                  GlobalOpenTelemetry.get(), "general", "db", "statement_sanitizer", "enabled")
              .orElse(true));

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(RedisCommand<?, ?, ?> request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Deprecated
  @Override
  public String getDbQueryText(RedisCommand<?, ?, ?> request) {
    String command = LettuceInstrumentationUtil.getCommandName(request);
    List<String> args =
        request.getArgs() == null
            ? Collections.emptyList()
            : LettuceArgSplitter.splitArgs(request.getArgs().toCommandString());
    return sanitizer.sanitize(command, args);
  }

  @Override
  public String getDbOperationName(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
