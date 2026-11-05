/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

public final class JedisServerError {
  @Nullable
  public static String errorType(@Nullable Throwable error) {
    // Preserve exception-class classification for dedicated Jedis exceptions.
    // Only generic JedisDataException messages are parsed for Redis error prefixes.
    if (error == null || error.getClass() != JedisDataException.class) {
      return null;
    }
    // Jedis's byte-by-byte decoding can corrupt non-ASCII server-error prefixes.
    // Fall back to the exception class rather than report a corrupted prefix.
    return RedisErrorPrefix.extractAscii(error.getMessage());
  }

  private JedisServerError() {}
}
