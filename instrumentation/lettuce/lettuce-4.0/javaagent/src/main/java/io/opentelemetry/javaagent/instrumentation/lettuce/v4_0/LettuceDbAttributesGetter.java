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

  @Override
  public String getDbSystem(RedisCommand<?, ?, ?> request) {
    return REDIS;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbNamespace(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
