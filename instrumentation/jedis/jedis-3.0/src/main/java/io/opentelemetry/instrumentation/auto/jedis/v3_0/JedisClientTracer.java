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

package io.opentelemetry.instrumentation.auto.jedis.v3_0;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.commands.ProtocolCommand;

public class JedisClientTracer extends DatabaseClientTracer<Connection, ProtocolCommand> {
  public static final JedisClientTracer TRACER = new JedisClientTracer();

  @Override
  protected String normalizeQuery(ProtocolCommand command) {
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), StandardCharsets.UTF_8);
    }
  }

  @Override
  protected String dbSystem(Connection connection) {
    return DbSystem.REDIS;
  }

  @Override
  protected String dbUser(Connection connection) {
    return null;
  }

  @Override
  protected String dbName(Connection connection) {
    return null;
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
    return "io.opentelemetry.auto.jedis-3.0";
  }
}
