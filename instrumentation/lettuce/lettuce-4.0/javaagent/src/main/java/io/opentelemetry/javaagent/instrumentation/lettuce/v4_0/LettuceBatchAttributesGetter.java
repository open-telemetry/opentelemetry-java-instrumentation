/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class LettuceBatchAttributesGetter
    implements DbClientAttributesGetter<LettuceBatchRequest, Void> {

  @Override
  @Nullable
  public String getErrorType(
      LettuceBatchRequest request, @Nullable Void response, @Nullable Throwable error) {
    return null;
  }

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
    RedisServerTarget serverTarget = request.getServerTarget();
    return serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(LettuceBatchRequest request) {
    RedisServerTarget serverTarget = request.getServerTarget();
    return serverTarget != null ? serverTarget.getPort() : null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(LettuceBatchRequest request, @Nullable Void unused) {
    return LettuceCommandPeer.getNetworkPeerAddress(request.getPeerAddress());
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(LettuceBatchRequest request, @Nullable Void unused) {
    return LettuceCommandPeer.getNetworkPeerPort(request.getPeerAddress());
  }
}
