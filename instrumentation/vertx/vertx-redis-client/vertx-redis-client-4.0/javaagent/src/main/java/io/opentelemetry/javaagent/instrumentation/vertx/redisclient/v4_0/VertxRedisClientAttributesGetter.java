/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import io.vertx.redis.client.impl.types.ErrorType;
import java.util.List;
import javax.annotation.Nullable;

final class VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Object> {

  @Override
  public String getDbSystemName(VertxRedisClientRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxRedisClientRequest request) {
    return request.getDatabaseNamespace();
  }

  @Override
  @Nullable
  public String getDbQueryText(VertxRedisClientRequest request) {
    return request.getQueryText();
  }

  @Nullable
  @Override
  public String getDbOperationName(VertxRedisClientRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(VertxRedisClientRequest request) {
    return request.getOperationBatchSize();
  }

  @Nullable
  @Override
  public String getServerAddress(VertxRedisClientRequest request) {
    if (request.isServerTargetCaptured()) {
      RedisServerTarget target = request.getServerTarget();
      return target != null ? target.getAddress() : null;
    }
    return request.getServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxRedisClientRequest request) {
    if (request.isServerTargetCaptured()) {
      RedisServerTarget target = request.getServerTarget();
      return target != null ? target.getPort() : null;
    }
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(VertxRedisClientRequest request, @Nullable Object unused) {
    return request.getPeerAddress();
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(VertxRedisClientRequest request, @Nullable Object unused) {
    return request.getPeerPort();
  }

  @Override
  @Nullable
  public String getErrorType(
      VertxRedisClientRequest request, @Nullable Object response, @Nullable Throwable error) {
    return getRedisErrorType(error);
  }

  // visible for testing
  @Nullable
  static String getRedisErrorType(@Nullable Throwable error) {
    if (!(error instanceof ErrorType)) {
      return null;
    }

    String message = error.getMessage();
    if (message == null || message.equals("CONNECTION_CLOSED")) {
      return null;
    }

    int terminator = message.indexOf("\r\n");
    if (terminator != -1) {
      message = message.substring(0, terminator);
    }

    // Vert.x 4.4.5 wraps batch failures as "ERR [index] <server reply>".
    if (message.startsWith("ERR [")) {
      int indexEnd = message.indexOf("] ");
      if (indexEnd > 5 && isBatchIndex(message, indexEnd)) {
        message = message.substring(indexEnd + 2);
      }
    }

    int start = 0;
    while (start < message.length() && message.charAt(start) == ' ') {
      start++;
    }
    if (start == message.length()) {
      return null;
    }

    int separator = message.indexOf(' ', start);
    return separator == -1 ? message.substring(start) : message.substring(start, separator);
  }

  @Nullable
  static Throwable getResponseError(@Nullable Object response) {
    if (response instanceof ErrorType) {
      return (ErrorType) response;
    }
    if (response instanceof List<?>) {
      for (Object item : (List<?>) response) {
        if (item instanceof ErrorType) {
          return (ErrorType) item;
        }
      }
    }
    return null;
  }

  private static boolean isBatchIndex(String message, int indexEnd) {
    for (int i = 5; i < indexEnd; i++) {
      if (message.charAt(i) < '0' || message.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }
}
