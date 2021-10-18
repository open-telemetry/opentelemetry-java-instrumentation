/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class RedissonDbAttributesExtractor extends DbAttributesExtractor<RedissonRequest, Void> {

  @Override
  protected String system(RedissonRequest request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Nullable
  @Override
  protected String user(RedissonRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String name(RedissonRequest request) {
    return null;
  }

  @Override
  protected String connectionString(RedissonRequest request) {
    return null;
  }

  @Override
  protected String statement(RedissonRequest request) {
    return request.getStatement();
  }

  @Nullable
  @Override
  protected String operation(RedissonRequest request) {
    return request.getOperation();
  }
}
