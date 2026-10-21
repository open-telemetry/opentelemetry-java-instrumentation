/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.ResponseType;
import io.vertx.redis.client.impl.types.ErrorType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Object> {

  private static final Logger logger =
      Logger.getLogger(VertxRedisClientAttributesGetter.class.getName());
  @Nullable private static final Method IS_MAP = findIsMap();

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
    VertxRedisClientError serverError = VertxRedisClientError.get(error);
    return serverError == null ? null : serverError.getErrorType();
  }

  @Nullable
  static Throwable getResponseError(@Nullable Object response) {
    if (response instanceof ErrorType) {
      return (ErrorType) response;
    }
    if (response instanceof List<?>) {
      for (Object item : (List<?>) response) {
        Throwable error = getResponseError(item);
        if (error != null) {
          return error;
        }
      }
    } else if (response instanceof Response && ((Response) response).type() == ResponseType.MULTI) {
      Response multi = (Response) response;
      if (IS_MAP != null) {
        try {
          if (Boolean.TRUE.equals(IS_MAP.invoke(multi))) {
            // These map iterators yield single-entry maps, so traverse their values directly.
            for (String key : multi.getKeys()) {
              Throwable error = getResponseError(multi.get(key));
              if (error != null) {
                return error;
              }
            }
            return null;
          }
        } catch (ReflectiveOperationException e) {
          logger.log(FINE, "Failed to inspect the Vert.x Redis response collection", e);
          return null;
        }
      }
      for (Response item : multi) {
        Throwable error = getResponseError(item);
        if (error != null) {
          return error;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Method findIsMap() {
    try {
      return Response.class.getMethod("isMap");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }
}
