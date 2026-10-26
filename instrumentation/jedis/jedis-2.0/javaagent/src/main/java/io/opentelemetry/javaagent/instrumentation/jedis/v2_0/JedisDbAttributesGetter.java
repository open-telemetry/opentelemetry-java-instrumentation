/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

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
  @Nullable
  public String getErrorType(
      JedisRequest request, @Nullable Void unused, @Nullable Throwable error) {
    Throwable originalError = error;
    // Jedis 2.3's Response.get() wraps protocol errors in another JedisDataException.
    if (error != null
        && error.getClass() == JedisDataException.class
        && !request.isServerError(error)) {
      error = error.getCause();
    }
    if (request.isServerError(error)) {
      String serverErrorType = JedisServerError.errorType(error);
      if (serverErrorType != null) {
        return serverErrorType;
      }
    }
    return originalError != null ? originalError.getClass().getName() : null;
  }

  @Override
  @Nullable
  public String getServerAddress(JedisRequest request) {
    RedisServerTarget target = JedisSingletons.connectionTarget(request.getConnection());
    return target != null ? target.getAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(JedisRequest request) {
    RedisServerTarget target = JedisSingletons.connectionTarget(request.getConnection());
    return target != null ? target.getPort() : null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      JedisRequest request, @Nullable Void unused) {
    return request.getPeerAddress();
  }
}
