/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
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
  public String getErrorType(
      RediscalaRequest request, @Nullable Void unused, @Nullable Throwable error) {
    if (!(error instanceof ReplyErrorException)) {
      return null;
    }

    String message = error.getMessage();
    if (message == null) {
      return null;
    }

    int separator = message.indexOf(' ');
    String prefix = separator == -1 ? message : message.substring(0, separator);
    switch (prefix) {
      case "ERR":
      case "WRONGTYPE":
      case "NOAUTH":
      case "WRONGPASS":
      case "NOPERM":
      case "READONLY":
      case "LOADING":
      case "BUSY":
      case "MISCONF":
      case "OOM":
      case "EXECABORT":
      case "CROSSSLOT":
      case "MASTERDOWN":
      case "CLUSTERDOWN":
      case "TRYAGAIN":
      case "MOVED":
      case "ASK":
      case "NOSCRIPT":
      case "NOREPLICAS":
      case "UNBLOCKED":
      case "BUSYGROUP":
      case "NOGROUP":
      case "NOTBUSY":
        return prefix;
      default:
        return null;
    }
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
