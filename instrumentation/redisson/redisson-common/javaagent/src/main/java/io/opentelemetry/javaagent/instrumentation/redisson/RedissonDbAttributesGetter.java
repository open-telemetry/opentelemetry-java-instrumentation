/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.REDIS;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import javax.annotation.Nullable;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest> {

  @Deprecated
  @Override
  public String getSystem(RedissonRequest request) {
    return REDIS;
  }

  @Override
  public String getDbSystem(RedissonRequest redissonRequest) {
    return REDIS;
  }

  @Deprecated
  @Nullable
  @Override
  public String getUser(RedissonRequest request) {
    return null;
  }

  @Deprecated
  @Nullable
  @Override
  public String getName(RedissonRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedissonRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getConnectionString(RedissonRequest request) {
    return null;
  }

  @Deprecated
  @Override
  public String getStatement(RedissonRequest request) {
    return request.getStatement();
  }

  @Override
  public String getDbQueryText(RedissonRequest request) {
    return request.getStatement();
  }

  @Deprecated
  @Nullable
  @Override
  public String getOperation(RedissonRequest request) {
    return request.getOperation();
  }

  @Nullable
  @Override
  public String getDbOperationName(RedissonRequest request) {
    return request.getOperation();
  }
}
