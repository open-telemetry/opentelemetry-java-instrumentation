/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

public enum VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest> {
  INSTANCE;

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(CommonConfig.get().isStatementSanitizationEnabled());

  @Override
  public String getSystem(VertxRedisClientRequest request) {
    return DbIncubatingAttributes.DbSystemValues.REDIS;
  }

  @Override
  @Nullable
  public String getUser(VertxRedisClientRequest request) {
    return request.getUser();
  }

  @Override
  @Nullable
  public String getName(VertxRedisClientRequest request) {
    return null;
  }

  @Override
  public String getStatement(VertxRedisClientRequest request) {
    return sanitizer.sanitize(request.getCommand(), request.getArgs());
  }

  @Nullable
  @Override
  public String getOperation(VertxRedisClientRequest request) {
    return request.getCommand();
  }
}
