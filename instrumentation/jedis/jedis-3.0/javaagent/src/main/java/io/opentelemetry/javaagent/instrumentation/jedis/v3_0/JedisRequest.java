/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import java.util.List;
import redis.clients.jedis.Connection;

@AutoValue
public abstract class JedisRequest {

  public static JedisRequest create(Connection connection, String command, List<Object> args) {
    return new AutoValue_JedisRequest(connection, command, args);
  }

  public abstract Connection getConnection();

  public abstract String getCommand();

  public abstract List<Object> getArgs();

  public String getOperation() {
    return getCommand();
  }

  public String getStatement() {
    return RedisCommandSanitizer.sanitize(getOperation(), getArgs());
  }
}
