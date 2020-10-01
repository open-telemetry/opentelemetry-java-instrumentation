/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.redisson;

import io.netty.channel.Channel;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import java.net.InetSocketAddress;
import java.util.List;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

public class RedissonClientTracer extends DatabaseClientTracer<RedisConnection, Object> {

  public static final RedissonClientTracer TRACER = new RedissonClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.redisson-3.0";
  }

  @Override
  protected String normalizeQuery(Object args) {
    // get command
    if (args instanceof CommandsData) {
      List<CommandData<?, ?>> commands = ((CommandsData) args).getCommands();
      StringBuilder commandStrings = new StringBuilder();
      for (CommandData commandData : commands) {
        commandStrings.append(commandData.getCommand().getName()).append(";");
      }
      if (commandStrings.length() > 0) {
        commandStrings.deleteCharAt(commandStrings.length() - 1);
      }
      return commandStrings.toString();
    } else if (args instanceof CommandData) {
      return ((CommandData) args).getCommand().getName();
    }
    return "Redis Command";
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
