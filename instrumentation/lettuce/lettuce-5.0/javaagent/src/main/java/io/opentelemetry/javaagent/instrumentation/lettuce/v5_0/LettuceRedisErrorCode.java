/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisCommandExecutionException;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;

public final class LettuceRedisErrorCode {

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (error == null) {
      return null;
    }

    String errorCode = getErrorCode(error);
    return errorCode != null ? errorCode : error.getClass().getName();
  }

  @Nullable
  static String getErrorCode(@Nullable Throwable error) {
    if (!(error instanceof RedisCommandExecutionException)) {
      return null;
    }
    // Lettuce versions before 6.5.4 decode server errors byte-by-byte instead of as UTF-8.
    // Do not report a corrupted non-ASCII prefix.
    // See https://github.com/redis/lettuce/pull/3188.
    return RedisErrorPrefix.extractAscii(error.getMessage());
  }

  private LettuceRedisErrorCode() {}
}
