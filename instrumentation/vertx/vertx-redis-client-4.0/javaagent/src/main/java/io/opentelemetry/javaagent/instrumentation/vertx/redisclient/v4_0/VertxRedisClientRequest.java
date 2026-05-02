/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.vertx.core.net.NetSocket;
import io.vertx.redis.client.impl.RedisURI;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

class VertxRedisClientRequest {
  private final String command;
  private final List<byte[]> args;
  private final RedisURI redisUri;
  private final NetSocket netSocket;

  VertxRedisClientRequest(
      String command, List<byte[]> args, RedisURI redisUri, NetSocket netSocket) {
    this.command = command.toUpperCase(Locale.ROOT);
    this.args = args;
    this.redisUri = redisUri;
    this.netSocket = netSocket;
  }

  String getCommand() {
    return command;
  }

  List<byte[]> getArgs() {
    return args;
  }

  @Nullable
  String getUser() {
    return redisUri.user();
  }

  @Nullable
  Long getDatabaseIndex() {
    Integer select = redisUri.select();
    return select != null ? select.longValue() : null;
  }

  @Nullable
  String getConnectionString() {
    return null;
  }

  String getHost() {
    return redisUri.socketAddress().host();
  }

  @Nullable
  Integer getPort() {
    int port = redisUri.socketAddress().port();
    return port != -1 ? port : null;
  }

  String getPeerAddress() {
    return netSocket.remoteAddress().hostAddress();
  }

  @Nullable
  Integer getPeerPort() {
    int port = netSocket.remoteAddress().port();
    return port != -1 ? port : null;
  }
}
