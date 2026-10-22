/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisCommandExecutionException;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;

public final class LettuceRedisErrorType {

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (!(error instanceof RedisCommandExecutionException)) {
      return null;
    }

    String errorType = RedisErrorPrefix.extract(error.getMessage());
    if (errorType == null) {
      return null;
    }
    // Older Lettuce versions decode server errors byte-by-byte instead of as UTF-8.
    // See https://github.com/redis/lettuce/blob/5.0.0.RELEASE/src/main/java/io/lettuce/core/output/CommandOutput.java#L83-L133.
    for (int i = 0; i < errorType.length(); i++) {
      if (errorType.charAt(i) > 0x7f) {
        return null;
      }
    }
    return errorType;
  }

  private LettuceRedisErrorType() {}
}
