/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest> {

  @Override
  public String getSystem(RedissonRequest request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Nullable
  @Override
  public String getUser(RedissonRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getName(RedissonRequest request) {
    return null;
  }

  @Override
  public String getConnectionString(RedissonRequest request) {
    return null;
  }

  @Override
  public String getStatement(RedissonRequest request) {
    return request.getStatement();
  }

  @Nullable
  @Override
  public String getOperation(RedissonRequest request) {
    return request.getOperation();
  }
}
