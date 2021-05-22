/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import java.util.Arrays;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

public final class JedisRequest {
  private static final byte[][] NO_ARGS = new byte[0][];

  private final Connection connection;
  private final Protocol.Command command;
  private final byte[][] args;

  public JedisRequest(Connection connection, Protocol.Command command, byte[][] args) {
    this.connection = connection;
    this.command = command;
    this.args = args;
  }

  public JedisRequest(Connection connection, Protocol.Command command) {
    this(connection, command, NO_ARGS);
  }

  public Connection getConnection() {
    return connection;
  }

  public String getCommand() {
    return command.name();
  }

  public String getStatement() {
    return RedisCommandSanitizer.sanitize(command.name(), Arrays.asList(args));
  }
}
