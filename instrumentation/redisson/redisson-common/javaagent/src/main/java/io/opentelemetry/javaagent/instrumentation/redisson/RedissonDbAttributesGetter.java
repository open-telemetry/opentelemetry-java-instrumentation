/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest> {

  @Override
  public String getSystem(RedissonRequest request) {
    return DbIncubatingAttributes.DbSystemValues.REDIS;
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
