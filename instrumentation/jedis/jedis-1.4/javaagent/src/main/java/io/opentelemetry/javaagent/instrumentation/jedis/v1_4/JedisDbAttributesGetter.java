/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jedis"));

  @Override
  public String getDbSystemName(JedisRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(JedisRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(JedisRequest request) {
    return sanitizer.sanitize(request.getCommand().name(), request.getArgs());
  }

  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getCommand().name();
  }

  @Override
  public String getServerAddress(JedisRequest request) {
    RedisServerTarget target = JedisSingletons.connectionTarget(request.getConnection());
    if (emitStableDatabaseSemconv()) {
      return target != null ? target.getAddress() : null;
    }
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    RedisServerTarget target = JedisSingletons.connectionTarget(request.getConnection());
    if (emitStableDatabaseSemconv()) {
      return target != null ? target.getPort() : null;
    }
    return request.getConnection().getPort();
  }
}
