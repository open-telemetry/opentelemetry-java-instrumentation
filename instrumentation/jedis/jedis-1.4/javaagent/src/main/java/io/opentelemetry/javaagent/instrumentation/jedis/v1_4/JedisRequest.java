/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Collections.emptyList;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

@AutoValue
public abstract class JedisRequest {
  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jedis"));

  public static JedisRequest create(Connection connection, Protocol.Command command) {
    return create(connection, command, emptyList());
  }

  public static JedisRequest create(
      Connection connection, Protocol.Command command, List<byte[]> args) {
    String operationName = command.name();
    return new AutoValue_JedisRequest(
        connection, operationName, sanitizer.sanitize(operationName, args));
  }

  public abstract Connection getConnection();

  public abstract String getOperationName();

  public abstract String getQueryText();
}
