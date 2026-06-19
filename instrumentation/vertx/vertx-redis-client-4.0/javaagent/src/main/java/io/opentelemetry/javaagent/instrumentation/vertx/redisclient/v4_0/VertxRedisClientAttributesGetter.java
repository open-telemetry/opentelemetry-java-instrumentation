/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

class VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Void> {

  @Override
  public String getDbSystemName(VertxRedisClientRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getUser(VertxRedisClientRequest request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxRedisClientRequest request) {
    if (emitStableDatabaseSemconv()) {
      Long databaseIndex = request.getDatabaseIndex();
      return databaseIndex == null ? null : String.valueOf(databaseIndex);
    }
    return null;
  }

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getConnectionString(VertxRedisClientRequest request) {
    return request.getConnectionString();
  }

  @Override
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
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxRedisClientRequest request) {
    return request.getPort();
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
