/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
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
    return null;
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
    if (emitStableDatabaseSemconv()) {
      RedisServerTarget serverTarget = request.getServerTarget();
      return serverTarget != null ? serverTarget.getAddress() : null;
    }
    InetSocketAddress serverAddress = request.getServerAddress();
    return serverAddress != null ? serverAddress.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(LettuceBatchRequest request) {
    if (emitStableDatabaseSemconv()) {
      RedisServerTarget serverTarget = request.getServerTarget();
      return serverTarget != null ? serverTarget.getPort() : null;
    }
    InetSocketAddress serverAddress = request.getServerAddress();
    return serverAddress != null ? serverAddress.getPort() : null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(LettuceBatchRequest request, @Nullable Void unused) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    InetSocketAddress peerAddress = request.getPeerAddress();
    return peerAddress != null && !peerAddress.isUnresolved()
        ? peerAddress.getAddress().getHostAddress()
        : null;
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(LettuceBatchRequest request, @Nullable Void unused) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    InetSocketAddress peerAddress = request.getPeerAddress();
    return peerAddress != null && !peerAddress.isUnresolved() ? peerAddress.getPort() : null;
  }
}
