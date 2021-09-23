/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class LettuceDbAttributesExtractor
    extends DbAttributesExtractor<RedisCommand<?, ?, ?>, Void> {
  @Override
  protected String system(RedisCommand<?, ?, ?> request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Override
  @Nullable
  protected String user(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  protected String name(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  protected String connectionString(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  protected String statement(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  protected String operation(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
