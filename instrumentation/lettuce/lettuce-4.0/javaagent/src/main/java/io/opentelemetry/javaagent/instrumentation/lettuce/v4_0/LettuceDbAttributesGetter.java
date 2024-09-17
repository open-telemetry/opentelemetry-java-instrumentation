/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.REDIS;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter implements DbClientAttributesGetter<RedisCommand<?, ?, ?>> {

  @Deprecated
  @Override
  public String getSystem(RedisCommand<?, ?, ?> request) {
    return REDIS;
  }

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

  @Deprecated
  @Override
  @Nullable
  public String getName(RedisCommand<?, ?, ?> request) {
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

  @Deprecated
  @Override
  public String getStatement(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbQueryText(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Deprecated
  @Override
  public String getOperation(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }

  @Nullable
  @Override
  public String getDbOperationName(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
