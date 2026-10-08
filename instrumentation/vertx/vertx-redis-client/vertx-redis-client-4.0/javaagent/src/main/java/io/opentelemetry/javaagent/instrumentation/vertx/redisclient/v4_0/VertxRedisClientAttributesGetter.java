/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Void> {

  @Override
  @Nullable
  public String getErrorType(
      VertxRedisClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    return null;
  }

  @Override
  public String getDbSystemName(VertxRedisClientRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxRedisClientRequest request) {
    return request.getDatabaseNamespace();
  }

  @Override
  @Nullable
  public String getDbQueryText(VertxRedisClientRequest request) {
    return request.getQueryText();
  }

  @Nullable
  @Override
  public String getDbOperationName(VertxRedisClientRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(VertxRedisClientRequest request) {
    return request.getOperationBatchSize();
  }

  @Nullable
  @Override
  public String getServerAddress(VertxRedisClientRequest request) {
    if (request.isServerTargetCaptured()) {
      RedisServerTarget target = request.getServerTarget();
      return target != null ? target.getAddress() : null;
    }
    return request.getServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxRedisClientRequest request) {
    if (request.isServerTargetCaptured()) {
      RedisServerTarget target = request.getServerTarget();
      return target != null ? target.getPort() : null;
    }
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(VertxRedisClientRequest request, @Nullable Void unused) {
    return request.getPeerAddress();
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(VertxRedisClientRequest request, @Nullable Void unused) {
    return request.getPeerPort();
  }
}
