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
  static String getErrorCode(@Nullable Throwable error) {
    return error instanceof ErrorType ? SERVER_ERROR.get((ErrorType) error) : null;
  }

  // Vert.x can complete successfully with errors inside a collection reply, such as EXEC results.
  // Find the first error to classify the telemetry.
  @Nullable
  static Throwable findResponseError(@Nullable Object response) {
    if (response == null) {
      return null;
    }
    try {
      // Redis replies can be deeply nested. Use an explicit stack to avoid stack overflow.
      Deque<Iterator<?>> pending = new ArrayDeque<>();
      pending.push(singletonList(response).iterator());
      while (!pending.isEmpty()) {
        Iterator<?> current = pending.peek();
        if (!current.hasNext()) {
          pending.pop();
          continue;
        }
        Object value = current.next();
        if (value instanceof ErrorType) {
          return (ErrorType) value;
        }
        Iterator<?> children = childIterator(value);
        if (children != null) {
          pending.push(children);
        }
      }
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to inspect the Vert.x Redis response collection", t);
    }
    return null;
  }

  @Nullable
  private static Iterator<?> childIterator(@Nullable Object value)
      throws ReflectiveOperationException {
    if (value instanceof List<?>) {
      return ((List<?>) value).iterator();
    }
    if (!(value instanceof Response) || ((Response) value).type() != ResponseType.MULTI) {
      return null;
    }
    Response multi = (Response) value;
    if (IS_MAP != null && Boolean.TRUE.equals(IS_MAP.invoke(multi))) {
      return mapValuesIterator(multi);
    }
    return multi.iterator();
  }

  private static Iterator<?> mapValuesIterator(Response map) {
    // Native map iterators yield single-entry maps; avoid building a string-keyed view of the
    // whole map, where distinct binary keys can collide after decoding.
    return map.stream().map(entry -> entry.get(entry.getKeys().iterator().next())).iterator();
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
