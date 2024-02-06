/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import io.vertx.core.net.NetSocket;
import io.vertx.redis.client.impl.RedisURI;
import java.util.List;
import java.util.Locale;

public final class VertxRedisClientRequest {
  private final String command;
  private final List<byte[]> args;
  private final RedisURI redisUri;
  private final NetSocket netSocket;

  public VertxRedisClientRequest(
      String command, List<byte[]> args, RedisURI redisUri, NetSocket netSocket) {
    this.command = command.toUpperCase(Locale.ROOT);
    this.args = args;
    this.redisUri = redisUri;
    this.netSocket = netSocket;
  }

  public String getCommand() {
    return command;
  }

  public List<byte[]> getArgs() {
    return args;
  }

  public String getUser() {
    return redisUri.user();
  }

  public Long getDatabaseIndex() {
    Integer select = redisUri.select();
    return select != null ? select.longValue() : null;
  }

  public String getConnectionString() {
    return null;
  }

  public String getHost() {
    return redisUri.socketAddress().host();
  }

  public Integer getPort() {
    int port = redisUri.socketAddress().port();
    return port != -1 ? port : null;
  }

  public String getPeerAddress() {
    return netSocket.remoteAddress().hostAddress();
  }

  public Integer getPeerPort() {
    int port = netSocket.remoteAddress().port();
    return port != -1 ? port : null;
  }
}
