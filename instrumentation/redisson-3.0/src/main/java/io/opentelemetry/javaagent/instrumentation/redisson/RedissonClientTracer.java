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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

public class RedissonClientTracer extends DatabaseClientTracer<RedisConnection, Object> {

  public static final RedissonClientTracer TRACER = new RedissonClientTracer();

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
    return "Redis Command";
  }

  private static String normalizeSingleCommand(CommandData<?, ?> command) {
    List<String> args = new ArrayList<>();
    if (command.getCommand().getSubName() != null) {
      args.add(command.getCommand().getSubName());
    }
    args.addAll(Stream.of(command.getParams()).map(String::valueOf).collect(Collectors.toList()));
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
