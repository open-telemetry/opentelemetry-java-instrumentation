/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.util.Collections.emptyList;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter
    implements DbClientAttributesGetter<RedisCommand<?, ?, ?>, Void> {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "lettuce"));

  @Override
  public String getDbSystemName(RedisCommand<?, ?, ?> request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  public String getDbQueryText(RedisCommand<?, ?, ?> request) {
    String command = LettuceInstrumentationUtil.getCommandName(request);
    List<String> args =
        request.getArgs() == null
            ? emptyList()
            : LettuceArgSplitter.splitArgs(request.getArgs().toCommandString());
    return sanitizer.sanitize(command, args);
  }

  @Override
  public String getDbOperationName(RedisCommand<?, ?, ?> request) {
    // use toString() (not name()) so it stays compatible with lettuce 6.5+, where ProtocolKeyword
    // no longer declares name()
    return request.getType().toString();
  }

  @Nullable
  @Override
  public String getServerAddress(RedisCommand<?, ?, ?> request) {
    RedisURI redisUri = LettuceSingletons.COMMAND_URI.get(request);
    return redisUri != null ? redisUri.getHost() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedisCommand<?, ?, ?> request) {
    RedisURI redisUri = LettuceSingletons.COMMAND_URI.get(request);
    return redisUri != null ? redisUri.getPort() : null;
  }
}
