/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.vertx.core.net.NetSocket;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.impl.RedisURI;
import io.vertx.redis.client.impl.RequestUtil;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

class VertxRedisClientRequest {
  // Match RedisCommandSanitizer's per-command limit when joining batch query text.
  private static final int BATCH_QUERY_TEXT_LIMIT = 32 * 1024;

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "vertx_redis_client"));

  @Nullable private final String operationName;
  @Nullable private final String queryText;
  @Nullable private final Long operationBatchSize;
  @Nullable private final RedisURI redisUri;
  private final NetSocket netSocket;

  VertxRedisClientRequest(
      @Nullable String command,
      List<byte[]> args,
      @Nullable RedisURI redisUri,
      NetSocket netSocket) {
    this(
        command,
        command == null ? null : sanitize(command.toUpperCase(Locale.ROOT), args),
        null,
        redisUri,
        netSocket);
  }

  private VertxRedisClientRequest(
      @Nullable String operationName,
      @Nullable String queryText,
      @Nullable Long operationBatchSize,
      @Nullable RedisURI redisUri,
      NetSocket netSocket) {
    this.operationName = operationName == null ? null : operationName.toUpperCase(Locale.ROOT);
    this.queryText = queryText;
    this.operationBatchSize = operationBatchSize;
    this.redisUri = redisUri;
    this.netSocket = netSocket;
  }

  static VertxRedisClientRequest createBatch(
      List<Request> requests, @Nullable RedisURI redisUri, NetSocket netSocket) {
    return new VertxRedisClientRequest(
        batchOperationName(requests),
        batchQueryText(requests),
        requests.size() != 1 ? (long) requests.size() : null,
        redisUri,
        netSocket);
  }

  @Nullable
  String getQueryText() {
    return queryText;
  }

  @Nullable
  String getOperationName() {
    return operationName;
  }

  @Nullable
  Long getOperationBatchSize() {
    return operationBatchSize;
  }

  @Nullable
  String getUser() {
    return redisUri == null ? null : redisUri.user();
  }

  @Nullable
  Long getDatabaseIndex() {
    if (redisUri == null) {
      return null;
    }
    Integer select = redisUri.select();
    return select != null ? select.longValue() : null;
  }

  @Nullable
  String getConnectionString() {
    return null;
  }

  @Nullable
  String getHost() {
    return redisUri == null ? null : redisUri.socketAddress().host();
  }

  @Nullable
  Integer getPort() {
    if (redisUri == null) {
      return null;
    }
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
    if (requests.isEmpty()) {
      return "PIPELINE";
    }
    String operationName = commandName(requests.get(0));
    if (operationName == null) {
      // Fall back to a generic span name when a command name can't be resolved.
      return "PIPELINE";
    }
    if (requests.size() == 1) {
      return operationName;
    }
    for (int i = 1; i < requests.size(); i++) {
      if (!operationName.equals(commandName(requests.get(i)))) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + operationName;
  }

  private static String batchQueryText(List<Request> requests) {
    StringBuilder builder = new StringBuilder();
    for (Request request : requests) {
      String commandName = commandName(request);
      if (commandName == null) {
        continue;
      }
      String queryText =
          sanitize(commandName.toUpperCase(Locale.ROOT), RequestUtil.getArgs(request));
      int newLength = builder.length();
      if (builder.length() > 0) {
        newLength++;
      }
      newLength += queryText.length();
      if (newLength > BATCH_QUERY_TEXT_LIMIT) {
        break;
      }
      if (builder.length() > 0) {
        builder.append(';');
      }
      builder.append(queryText);
    }
    return builder.toString();
  }

  @Nullable
  private static String commandName(@Nullable Request request) {
    return request != null ? VertxRedisClientSingletons.getCommandName(request.command()) : null;
  }

  private static String sanitize(String command, List<byte[]> args) {
    return sanitizer.sanitize(command, args);
  }
}
