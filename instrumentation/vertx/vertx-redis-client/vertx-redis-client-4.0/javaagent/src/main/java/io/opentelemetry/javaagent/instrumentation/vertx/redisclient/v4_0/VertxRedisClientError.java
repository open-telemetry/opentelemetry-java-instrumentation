/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.ResponseType;
import io.vertx.redis.client.impl.types.ErrorType;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class VertxRedisClientError {
  private static final Logger logger = Logger.getLogger(VertxRedisClientError.class.getName());
  @Nullable private static final Method IS_MAP = findIsMap();
  private static final VirtualField<ErrorType, String> SERVER_ERROR =
      VirtualField.find(ErrorType.class, String.class);

  public static void captureServerError(ErrorType error) {
    String reply = error.getMessage();
    if (reply != null) {
      // ReadableBuffer.readLine maps wire bytes to ISO-8859-1, preserving them for UTF-8 decoding.
      reply = new String(reply.getBytes(ISO_8859_1), UTF_8);
    }
    SERVER_ERROR.set(error, RedisErrorPrefix.extract(reply));
  }

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    return error instanceof ErrorType ? SERVER_ERROR.get((ErrorType) error) : null;
  }

  @Nullable
  static Throwable findResponseError(@Nullable Object response) {
    try {
      if (response == null) {
        return null;
      }
      Deque<Iterator<?>> stack = new ArrayDeque<>();
      stack.push(singletonList(response).iterator());
      while (!stack.isEmpty()) {
        Iterator<?> iterator = stack.peek();
        if (!iterator.hasNext()) {
          stack.pop();
          continue;
        }
        Object item = iterator.next();
        if (item instanceof ErrorType) {
          return (ErrorType) item;
        }
        if (item instanceof List<?>) {
          stack.push(((List<?>) item).iterator());
        } else if (item instanceof Response && ((Response) item).type() == ResponseType.MULTI) {
          stack.push(children((Response) item));
        }
      }
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to inspect the Vert.x Redis response collection", t);
    }
    return null;
  }

  private static Iterator<?> children(Response multi) throws ReflectiveOperationException {
    if (IS_MAP != null && Boolean.TRUE.equals(IS_MAP.invoke(multi))) {
      // Native map iterators yield single-entry maps; avoid building a string-keyed view of the
      // whole map, where distinct binary keys can collide after decoding.
      return multi.stream().map(entry -> entry.get(entry.getKeys().iterator().next())).iterator();
    }
    return multi.iterator();
  }

  @Nullable
  private static Method findIsMap() {
    try {
      return Response.class.getMethod("isMap");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private VertxRedisClientError() {}
}
