/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

class RediscalaAttributesGetter implements DbClientAttributesGetter<RediscalaRequest, Void> {

  @Override
  public String getDbSystemName(RediscalaRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(RediscalaRequest request) {
    // Rediscala only selects the database when the connection is established, so a SELECT sent
    // later by application code is not reflected here.
    Integer databaseIndex = request.getDatabaseIndex();
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getDbName(RediscalaRequest request) {
    // old semconv reports the redis database index as db.redis.database_index, not db.name
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(RediscalaRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(RediscalaRequest request) {
    return request.getStableOperationName();
  }

  @Override
  @SuppressWarnings("deprecation") // old database semconv still use db.operation
  public String getDbOperation(RediscalaRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(RediscalaRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getServerAddress(RediscalaRequest request) {
    RedisServerTarget serverTarget = request.getServerTarget();
    if (emitStableDatabaseSemconv() && serverTarget != null) {
      return serverTarget.getAddress();
    }
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(RediscalaRequest request) {
    RedisServerTarget serverTarget = request.getServerTarget();
    if (emitStableDatabaseSemconv() && serverTarget != null) {
      // a target that names several endpoints already carries the port of each of them
      return serverTarget.getPort();
    }
    return request.getPort();
  }
}
