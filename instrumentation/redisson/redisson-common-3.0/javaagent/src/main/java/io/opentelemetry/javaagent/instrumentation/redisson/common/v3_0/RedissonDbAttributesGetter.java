/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class RedissonDbAttributesGetter implements DbClientAttributesGetter<RedissonRequest, Void> {

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

  @Nullable
  @Override
  public String getDbName(RedissonRequest request) {
    return null;
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

  @Nullable
  @Override
  public String getServerAddress(RedissonRequest request) {
    RedisServerTarget target = request.getServerTarget();
    if (emitStableDatabaseSemconv() && target != null) {
      return target.getAddress();
    }
    InetSocketAddress address = request.getAddress();
    return address != null ? address.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedissonRequest request) {
    RedisServerTarget target = request.getServerTarget();
    if (emitStableDatabaseSemconv() && target != null) {
      // a target that names several endpoints already carries the port of each of them
      return target.getPort();
    }
    InetSocketAddress address = request.getAddress();
    return address != null ? address.getPort() : null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }
}
