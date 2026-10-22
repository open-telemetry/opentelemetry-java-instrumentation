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
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class VertxRedisClientAttributesGetter
    implements DbClientAttributesGetter<VertxRedisClientRequest, Void> {

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
  public String getNetworkPeerAddress(VertxRedisClientRequest request, @Nullable Void unused) {
    return request.getPeerAddress();
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(VertxRedisClientRequest request, @Nullable Void unused) {
    return request.getPeerPort();
  }

  @Override
  @Nullable
  public String getErrorType(
      VertxRedisClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    return getRedisErrorType(error);
  }

  // visible for testing
  @Nullable
  static String getRedisErrorType(@Nullable Throwable error) {
    return VertxRedisClientError.getErrorType(error);
  }

  @Nullable
  static Throwable getResponseError(@Nullable Object response) {
    if (response == null) {
      return null;
    }

    Deque<Object> pending = new ArrayDeque<>();
    pending.addFirst(response);
    while (!pending.isEmpty()) {
      Object item = pending.removeFirst();
      if (item instanceof ErrorType) {
        return (ErrorType) item;
      }
      if (item instanceof List<?>) {
        List<?> list = (List<?>) item;
        ListIterator<?> iterator = list.listIterator(list.size());
        while (iterator.hasPrevious()) {
          addFirst(pending, iterator.previous());
        }
      } else if (item.getClass().isArray()) {
        for (int i = Array.getLength(item) - 1; i >= 0; i--) {
          addFirst(pending, Array.get(item, i));
        }
      } else if (item instanceof Map<?, ?>) {
        List<?> values = new ArrayList<>(((Map<?, ?>) item).values());
        for (int i = values.size() - 1; i >= 0; i--) {
          addFirst(pending, values.get(i));
        }
      } else if (item instanceof Response && ((Response) item).type() == ResponseType.MULTI) {
        Response multi = (Response) item;
        if (IS_MAP != null) {
          try {
            if (Boolean.TRUE.equals(IS_MAP.invoke(multi))) {
              // These map iterators yield single-entry maps, so traverse their values directly.
              List<String> keys = new ArrayList<>(multi.getKeys());
              for (int i = keys.size() - 1; i >= 0; i--) {
                addFirst(pending, multi.get(keys.get(i)));
              }
              continue;
            }
          } catch (ReflectiveOperationException e) {
            logger.log(FINE, "Failed to inspect the Vert.x Redis response collection", e);
            continue;
          }
        }
        List<Response> children = new ArrayList<>();
        for (Response child : multi) {
          children.add(child);
        }
        for (int i = children.size() - 1; i >= 0; i--) {
          addFirst(pending, children.get(i));
        }
      }
    }
    return null;
  }

  private static void addFirst(Deque<Object> pending, @Nullable Object response) {
    if (response != null) {
      pending.addFirst(response);
    }
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
