/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoValue
public abstract class JedisRequest {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  public static JedisRequest create(
      Connection connection, ProtocolCommand command, List<byte[]> args) {
    return new AutoValue_JedisRequest(connection, command, args);
  }

  public abstract Connection getConnection();

  public abstract ProtocolCommand getCommand();

  public abstract List<byte[]> getArgs();

  public String getOperation() {
    ProtocolCommand command = getCommand();
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), StandardCharsets.UTF_8);
    }
  }

  public String getStatement() {
    return sanitizer.sanitize(getOperation(), getArgs());
  }
}
