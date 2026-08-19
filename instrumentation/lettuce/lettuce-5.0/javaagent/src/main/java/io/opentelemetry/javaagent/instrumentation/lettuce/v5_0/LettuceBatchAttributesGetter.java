/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

class LettuceBatchAttributesGetter implements DbClientAttributesGetter<LettuceBatchRequest, Void> {

  @Override
  public String getDbSystemName(LettuceBatchRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(LettuceBatchRequest request) {
    Integer databaseIndex = request.getDatabaseIndex();
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getDbName(LettuceBatchRequest request) {
    // old semconv reports the redis database index as db.redis.database_index, not db.name
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(LettuceBatchRequest request) {
    return request.getQueryText();
  }

  @Override
  public String getDbOperationName(LettuceBatchRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(LettuceBatchRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getServerAddress(LettuceBatchRequest request) {
    RedisURI redisUri = request.getRedisUri();
    return redisUri != null ? redisUri.getHost() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(LettuceBatchRequest request) {
    RedisURI redisUri = request.getRedisUri();
    return redisUri != null ? redisUri.getPort() : null;
  }
}
