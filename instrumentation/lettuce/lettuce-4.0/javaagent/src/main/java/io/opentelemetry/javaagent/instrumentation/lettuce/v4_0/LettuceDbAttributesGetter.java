/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter implements DbClientAttributesGetter<RedisCommand<?, ?, ?>> {

  @Override
  public String system(RedisCommand<?, ?, ?> request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Override
  @Nullable
  public String user(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  public String name(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  public String statement(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  public String operation(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
