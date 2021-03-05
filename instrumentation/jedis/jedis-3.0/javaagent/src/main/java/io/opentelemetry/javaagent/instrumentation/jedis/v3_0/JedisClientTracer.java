/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisClientTracer.CommandWithArgs;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.commands.ProtocolCommand;

public class JedisClientTracer extends DatabaseClientTracer<Connection, CommandWithArgs, String> {
  private static final JedisClientTracer TRACER = new JedisClientTracer();

  public static JedisClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String sanitizeStatement(CommandWithArgs command) {
    return RedisCommandSanitizer.sanitize(command.getStringCommand(), command.getArgs());
  }

  @Override
  protected String spanName(
      Connection connection, CommandWithArgs command, String sanitizedStatement) {
    return command.getStringCommand();
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
  protected String dbStatement(
      Connection connection, CommandWithArgs command, String sanitizedStatement) {
    return sanitizedStatement;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jedis-3.0";
  }

  public static final class CommandWithArgs {
    private final ProtocolCommand command;
    private final byte[][] args;

    public CommandWithArgs(ProtocolCommand command, byte[][] args) {
      this.command = command;
      this.args = args;
    }

    private String getStringCommand() {
      if (command instanceof Protocol.Command) {
        return ((Protocol.Command) command).name();
      } else {
        // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
        // us if that changes
        return new String(command.getRaw(), StandardCharsets.UTF_8);
      }
    }

    private List<?> getArgs() {
      return Arrays.asList(args);
    }
  }
}
