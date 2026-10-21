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

  static LettuceResponse unknownFailure() {
    return new LettuceResponse(true, null);
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

    int start = 0;
    while (start < errorMessage.length() && errorMessage.charAt(start) == ' ') {
      start++;
    }
    if (start == errorMessage.length()) {
      return null;
    }
    int separator = errorMessage.indexOf(' ', start);
    return separator == -1
        ? errorMessage.substring(start)
        : errorMessage.substring(start, separator);
  }
}
