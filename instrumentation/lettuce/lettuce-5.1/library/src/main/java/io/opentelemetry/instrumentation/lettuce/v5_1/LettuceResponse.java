/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import javax.annotation.Nullable;

final class LettuceResponse {

  private final boolean failed;
  @Nullable private final String redisErrorType;

  static LettuceResponse fromErrorMessage(@Nullable String errorMessage) {
    return new LettuceResponse(errorMessage != null, extractRedisErrorPrefix(errorMessage));
  }

  private LettuceResponse(boolean failed, @Nullable String redisErrorType) {
    this.failed = failed;
    this.redisErrorType = redisErrorType;
  }

  boolean isFailed() {
    return failed;
  }

  @Nullable
  String getRedisErrorType() {
    return redisErrorType;
  }

  @Nullable
  private static String extractRedisErrorPrefix(@Nullable String errorMessage) {
    if (errorMessage == null || errorMessage.isEmpty()) {
      return null;
    }

    // Redis error prefixes are the first nonempty, ASCII-space-delimited token of an error reply.
    int end = errorMessage.indexOf("\r\n");
    if (end == -1) {
      end = errorMessage.length();
    }
    int separator = errorMessage.indexOf(' ');
    if (separator == -1 || separator > end) {
      separator = end;
    }
    return separator == 0 ? null : errorMessage.substring(0, separator);
  }
}
