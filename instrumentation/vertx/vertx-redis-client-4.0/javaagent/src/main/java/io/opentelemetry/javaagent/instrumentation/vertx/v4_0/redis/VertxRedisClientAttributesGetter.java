/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

public enum VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Void> {
  INSTANCE;

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

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
      return String.valueOf(request.getDatabaseIndex());
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
    return sanitizer.sanitize(request.getCommand(), request.getArgs());
  }

  @Nullable
  @Override
  public String getDbOperationName(VertxRedisClientRequest request) {
    return request.getCommand();
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
