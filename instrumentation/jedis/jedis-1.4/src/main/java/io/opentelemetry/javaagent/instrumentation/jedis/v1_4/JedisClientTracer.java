/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.jdbc.DbSystem;
import java.net.InetSocketAddress;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol.Command;

public class JedisClientTracer extends DatabaseClientTracer<Connection, Command> {
  public static final JedisClientTracer TRACER = new JedisClientTracer();

  @Override
  protected String normalizeQuery(Command command) {
    return command.name();
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
}
