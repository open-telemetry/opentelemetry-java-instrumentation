/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.redis.client.impl.types.ErrorType;
import javax.annotation.Nullable;

public final class VertxRedisClientError {
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

  private VertxRedisClientError() {}
}
