/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisCommandExecutionException;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisErrorPrefix;
import javax.annotation.Nullable;

public final class LettuceRedisErrorType {

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (!(error instanceof RedisCommandExecutionException)) {
      return null;
    }

    return RedisErrorPrefix.extractAscii(error.getMessage());
  }

  private LettuceRedisErrorType() {}
}
