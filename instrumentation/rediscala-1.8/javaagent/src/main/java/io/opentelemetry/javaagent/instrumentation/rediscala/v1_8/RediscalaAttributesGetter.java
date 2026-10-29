/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;
import redis.actors.ReplyErrorException;

final class RediscalaAttributesGetter implements DbClientAttributesGetter<RediscalaRequest, Void> {

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

  @Override
  @Nullable
  public String getDbQueryText(RediscalaRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(RediscalaRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(RediscalaRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      RediscalaRequest request, @Nullable Void response, @Nullable Throwable error) {
    return getRedisErrorCode(error);
  }

  @Nullable
  @Override
  public String getErrorType(
      RediscalaRequest request, @Nullable Void unused, @Nullable Throwable error) {
    String errorCode = getRedisErrorCode(error);
    return errorCode != null ? errorCode : error == null ? null : error.getClass().getName();
  }

  @Nullable
  private static String getRedisErrorCode(@Nullable Throwable error) {
    // Rediscala wraps decoded Redis error replies in ReplyErrorException. RESP errors start with an
    // error prefix.
    // https://redis.io/docs/latest/develop/reference/protocol-spec/#resp-errors
    if (!(error instanceof ReplyErrorException)) {
      return null;
    }
    return RedisErrorPrefix.extract(error.getMessage());
  }

  @Nullable
  @Override
  public String getServerAddress(RediscalaRequest request) {
    RedisServerTarget serverTarget = request.getServerTarget();
    return serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RediscalaRequest request) {
    RedisServerTarget serverTarget = request.getServerTarget();
    return serverTarget != null ? serverTarget.getPort() : null;
  }
}
