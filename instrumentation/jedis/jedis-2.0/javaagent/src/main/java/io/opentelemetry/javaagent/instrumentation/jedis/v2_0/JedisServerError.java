/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

public final class JedisServerError {
  @Nullable
  public static String errorType(@Nullable Throwable error) {
    if (error == null || error.getClass() != JedisDataException.class) {
      return null;
    }
    return RedisErrorPrefix.extractAscii(error.getMessage());
  }

  private JedisServerError() {}
}
