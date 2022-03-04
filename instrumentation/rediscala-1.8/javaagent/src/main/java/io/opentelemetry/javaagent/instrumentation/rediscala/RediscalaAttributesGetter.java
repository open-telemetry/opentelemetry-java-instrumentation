/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import io.opentelemetry.instrumentation.api.instrumenter.ClassNames;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Locale;
import javax.annotation.Nullable;
import redis.RedisCommand;

final class RediscalaAttributesGetter implements DbClientAttributesGetter<RedisCommand<?, ?>> {

  @Override
  public String system(RedisCommand<?, ?> redisCommand) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Override
  @Nullable
  public String user(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Override
  @Nullable
  public String name(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Override
  @Nullable
  public String statement(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Override
  public String operation(RedisCommand<?, ?> redisCommand) {
    return ClassNames.simpleName(redisCommand.getClass()).toUpperCase(Locale.ROOT);
  }
}
