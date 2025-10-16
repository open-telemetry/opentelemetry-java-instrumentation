/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

public enum VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Void> {
  INSTANCE;

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  @SuppressWarnings("deprecation") // using deprecated DbSystemIncubatingValues
  @Override
  public String getDbSystem(VertxRedisClientRequest request) {
    return DbIncubatingAttributes.DbSystemIncubatingValues.REDIS;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(VertxRedisClientRequest request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxRedisClientRequest request) {
    if (SemconvStability.emitStableDatabaseSemconv()) {
      Long dbIndex = request.getDatabaseIndex();
      return dbIndex != null ? String.valueOf(dbIndex) : null;
    }
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(VertxRedisClientRequest request) {
    return request.getConnectionString();
  }

  @Override
  public String getDbQueryText(VertxRedisClientRequest request) {
    // Direct pass-through of byte arrays (efficient approach matching 4.0)
    return sanitizer.sanitize(request.getCommand(), request.getArgs());
  }

  @Nullable
  @Override
  public String getDbOperationName(VertxRedisClientRequest request) {
    return request.getCommand();
  }
}
