/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.netty.channel.Channel;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

public class RedissonClientTracer extends DatabaseClientTracer<RedisConnection, Object> {
  private static final String UNKNOWN_COMMAND = "Redis Command";

  public static final RedissonClientTracer TRACER = new RedissonClientTracer();

  @Override
  protected String spanName(RedisConnection connection, Object query, String normalizedQuery) {
    if (query instanceof CommandsData) {
      List<CommandData<?, ?>> commands = ((CommandsData) query).getCommands();
      StringBuilder commandStrings = new StringBuilder();
      for (CommandData<?, ?> commandData : commands) {
        commandStrings.append(commandData.getCommand().getName()).append(";");
      }
      if (commandStrings.length() > 0) {
        commandStrings.deleteCharAt(commandStrings.length() - 1);
      }
      return commandStrings.toString();
    } else if (query instanceof CommandData) {
      return ((CommandData<?, ?>) query).getCommand().getName();
    }

    return UNKNOWN_COMMAND;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.redisson";
  }

  @Override
  protected String normalizeQuery(Object command) {
    // get command
    if (command instanceof CommandsData) {
      List<CommandData<?, ?>> commands = ((CommandsData) command).getCommands();
      StringBuilder commandStrings = new StringBuilder();
      for (CommandData<?, ?> commandData : commands) {
        commandStrings.append(normalizeSingleCommand(commandData)).append(";");
      }
      if (commandStrings.length() > 0) {
        commandStrings.deleteCharAt(commandStrings.length() - 1);
      }
      return commandStrings.toString();
    } else if (command instanceof CommandData) {
      return normalizeSingleCommand((CommandData<?, ?>) command);
    }
    return UNKNOWN_COMMAND;
  }

  private static String normalizeSingleCommand(CommandData<?, ?> command) {
    Object[] commandParams = command.getParams();
    List<Object> args = new ArrayList<>(commandParams.length + 1);
    if (command.getCommand().getSubName() != null) {
      args.add(command.getCommand().getSubName());
    }
    args.addAll(Arrays.asList(commandParams));
    return RedisCommandNormalizer.normalize(command.getCommand().getName(), args);
  }

  @Override
  protected String dbSystem(RedisConnection connection) {
    return DbSystem.REDIS;
  }

  @Override
  protected InetSocketAddress peerAddress(RedisConnection connection) {
    Channel channel = connection.getChannel();
    return (InetSocketAddress) channel.remoteAddress();
  }

  @Override
  protected String dbConnectionString(RedisConnection connection) {
    Channel channel = connection.getChannel();
    InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
    return remoteAddress.getHostString() + ":" + remoteAddress.getPort();
  }
}
