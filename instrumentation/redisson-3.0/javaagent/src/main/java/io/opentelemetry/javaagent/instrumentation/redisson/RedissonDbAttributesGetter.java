/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest> {

  @Override
  public String system(RedissonRequest request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Nullable
  @Override
  public String user(RedissonRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String name(RedissonRequest request) {
    return null;
  }

  @Override
  public String connectionString(RedissonRequest request) {
    return null;
  }

  @Override
  public String statement(RedissonRequest request) {
    return request.getStatement();
  }

  @Nullable
  @Override
  public String operation(RedissonRequest request) {
    return request.getOperation();
  }
}
