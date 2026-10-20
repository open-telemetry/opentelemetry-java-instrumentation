/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.redisson.client.RedisException;

final class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest, Void> {

  @Override
  public String getDbSystemName(RedissonRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedissonRequest request) {
    Long databaseIndex = request.getDatabaseIndex();
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
  }

  @Override
  @Nullable
  public String getDbQueryText(RedissonRequest request) {
    return request.getQueryText();
  }

  @Nullable
  @Override
  public String getDbOperationName(RedissonRequest request) {
    return request.getOperationName();
  }

  @Nullable
  @Override
  public Long getDbOperationBatchSize(RedissonRequest request) {
    return request.getOperationBatchSize();
  }

  @Override
  @Nullable
  public String getErrorType(
      RedissonRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (error == null || error.getClass() != RedisException.class) {
      return null;
    }

    String message = error.getMessage();
    if (message == null) {
      return null;
    }

    int serverErrorMarker = message.indexOf(". channel:");
    if (serverErrorMarker < 0) {
      return null;
    }

    int separator = message.indexOf(' ');
    if (separator <= 0 || separator >= serverErrorMarker) {
      return null;
    }

    String errorType = message.substring(0, separator);
    switch (errorType) {
      case "ERR":
      case "WRONGTYPE":
      case "EXECABORT":
      case "CROSSSLOT":
      case "MASTERDOWN":
      case "NOREPLICAS":
      case "READONLY":
      case "MISCONF":
      case "NOPERM":
      case "NOSCRIPT":
      case "NOAUTH":
      case "WRONGPASS":
      case "MOVED":
      case "ASK":
      case "TRYAGAIN":
      case "LOADING":
      case "OOM":
      case "CLUSTERDOWN":
      case "BUSY":
      case "WAIT":
        return errorType;
      default:
        return null;
    }
  }

  @Nullable
  @Override
  public String getServerAddress(RedissonRequest request) {
    RedisServerTarget target = request.getServerTarget();
    return target != null ? target.getAddress() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedissonRequest request) {
    RedisServerTarget target = request.getServerTarget();
    return target != null ? target.getPort() : null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }
}
