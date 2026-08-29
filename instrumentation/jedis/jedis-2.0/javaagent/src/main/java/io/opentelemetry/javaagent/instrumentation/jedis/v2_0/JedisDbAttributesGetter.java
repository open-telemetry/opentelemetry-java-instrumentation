/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  @Override
  public String getDbSystemName(JedisRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(JedisRequest request) {
    Long databaseIndex = request.getDatabaseIndex();
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
  }

  @Override
  @Nullable
  public String getDbName(JedisRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(JedisRequest request) {
    return request.getQueryText();
  }

  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(JedisRequest request) {
    return request.getBatchSize();
  }

  @Override
  public String getServerAddress(JedisRequest request) {
    RedisServerTarget target = JedisSingletons.connectionTarget(request.getConnection());
    if (emitStableDatabaseSemconv()) {
      return target != null ? target.getAddress() : null;
    }
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    RedisServerTarget target = JedisSingletons.connectionTarget(request.getConnection());
    if (emitStableDatabaseSemconv()) {
      return target != null ? target.getPort() : null;
    }
    return request.getConnection().getPort();
  }
}
