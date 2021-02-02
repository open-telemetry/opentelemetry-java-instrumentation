/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.javaagent.instrumentation.jedis.v1_4.JedisClientTracer.CommandWithArgs;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol.Command;

public class JedisClientTracer extends DatabaseClientTracer<Connection, CommandWithArgs> {
  private static final JedisClientTracer TRACER = new JedisClientTracer();

  public static JedisClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String spanName(Connection connection, CommandWithArgs query, String normalizedQuery) {
    return query.getStringCommand();
  }

  @Override
  protected String normalizeQuery(CommandWithArgs command) {
    return RedisCommandSanitizer.sanitize(command.getStringCommand(), command.getArgs());
  }

  @Override
  protected String dbSystem(Connection connection) {
    return DbSystemValues.REDIS;
  }

  @Override
  protected String dbConnectionString(Connection connection) {
    return connection.getHost() + ":" + connection.getPort();
  }

  @Override
  protected InetSocketAddress peerAddress(Connection connection) {
    return new InetSocketAddress(connection.getHost(), connection.getPort());
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jedis";
  }

  public static final class CommandWithArgs {
    private static final byte[][] NO_ARGS = new byte[0][];

    private final Command command;
    private final byte[][] args;

    public CommandWithArgs(Command command, byte[][] args) {
      this.command = command;
      this.args = args;
    }

    public CommandWithArgs(Command command) {
      this(command, NO_ARGS);
    }

    private String getStringCommand() {
      return command.name();
    }

    private List<?> getArgs() {
      return Arrays.asList(args);
    }
  }
}
