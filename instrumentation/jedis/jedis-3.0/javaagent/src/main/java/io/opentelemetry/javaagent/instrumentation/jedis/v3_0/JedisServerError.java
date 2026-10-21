/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;
import redis.clients.jedis.exceptions.JedisDataException;

public final class JedisServerError {
  @Nullable
  public static String errorType(@Nullable Throwable error) {
    if (error == null || error.getClass() != JedisDataException.class) {
      return null;
    }
    String errorType = RedisErrorPrefix.extract(error.getMessage());
    return isAscii(errorType) ? errorType : null;
  }

  private static boolean isAscii(@Nullable String value) {
    if (value == null) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) > 0x7f) {
        return false;
      }
    }
    return true;
  }

  private JedisServerError() {}
}
