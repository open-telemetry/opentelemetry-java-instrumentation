/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer;
import io.opentelemetry.javaagent.instrumentation.jedis.v1_4.JedisClientTracer.CommandWithArgs;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol.Command;

public class JedisClientTracer extends DatabaseClientTracer<Connection, CommandWithArgs> {
  public static final JedisClientTracer TRACER = new JedisClientTracer();

  @Override
  protected String normalizeQuery(CommandWithArgs command) {
    return RedisCommandNormalizer.normalize(command.getStringCommand(), command.getArgs());
  }

  @Override
  protected String dbSystem(Connection connection) {
    return DbSystem.REDIS;
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
    return "io.opentelemetry.auto.jedis";
  }

  public static final class CommandWithArgs {
    private final Command command;
    private final byte[][] args;

    public CommandWithArgs(Command command, byte[][] args) {
      this.command = command;
      this.args = args;
    }

    private String getStringCommand() {
      return command.name();
    }

    private List<?> getArgs() {
      return Arrays.asList(args);
    }
  }
}
