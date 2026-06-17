/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.vertx.core.net.NetSocket;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.impl.RedisURI;
import io.vertx.redis.client.impl.RequestUtil;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import javax.annotation.Nullable;

class VertxRedisClientRequest {
  private final String command;
  private final List<byte[]> args;
  private final RedisURI redisUri;
  private final NetSocket netSocket;
  @Nullable private final String queryTextOverride;
  @Nullable private final Long batchSize;

  VertxRedisClientRequest(
      String command, List<byte[]> args, RedisURI redisUri, NetSocket netSocket) {
    this(command, args, redisUri, netSocket, null, null);
  }

  private VertxRedisClientRequest(
      String command,
      List<byte[]> args,
      RedisURI redisUri,
      NetSocket netSocket,
      @Nullable String queryTextOverride,
      @Nullable Long batchSize) {
    this.command = command.toUpperCase(Locale.ROOT);
    this.args = args;
    this.redisUri = redisUri;
    this.netSocket = netSocket;
    this.queryTextOverride = queryTextOverride;
    this.batchSize = batchSize;
  }

  static VertxRedisClientRequest createBatch(
      List<Request> requests, RedisURI redisUri, NetSocket netSocket) {
    return new VertxRedisClientRequest(
        batchOperationName(requests),
        RequestUtil.getArgs(requests.get(0)),
        redisUri,
        netSocket,
        batchQueryText(requests),
        requests.size() > 1 ? (long) requests.size() : null);
  }

  String getCommand() {
    return command;
  }

  List<byte[]> getArgs() {
    return args;
  }

  @Nullable
  String getQueryTextOverride() {
    return queryTextOverride;
  }

  @Nullable
  Long getBatchSize() {
    return batchSize;
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

  private static String batchOperationName(List<Request> requests) {
    String operationName = VertxRedisClientSingletons.getCommandName(requests.get(0).command());
    if (requests.size() == 1) {
      return operationName;
    }
    for (int i = 1; i < requests.size(); i++) {
      if (!operationName.equals(
          VertxRedisClientSingletons.getCommandName(requests.get(i).command()))) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + operationName;
  }

  private static String batchQueryText(List<Request> requests) {
    StringJoiner joiner = new StringJoiner(";");
    for (Request request : requests) {
      String commandName =
          VertxRedisClientSingletons.getCommandName(request.command()).toUpperCase(Locale.ROOT);
      joiner.add(
          VertxRedisClientAttributesGetter.sanitize(commandName, RequestUtil.getArgs(request)));
    }
    return joiner.toString();
  }
}
