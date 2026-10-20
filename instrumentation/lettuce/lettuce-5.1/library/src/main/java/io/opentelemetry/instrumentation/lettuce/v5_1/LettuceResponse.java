/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import java.util.Locale;
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

    // Redis error prefixes are the first upper-case, space-delimited word of an error reply.
    int separator = errorMessage.indexOf(' ');
    String errorType = separator == -1 ? errorMessage : errorMessage.substring(0, separator);
    return errorType.equals(errorType.toUpperCase(Locale.ROOT)) ? errorType : null;
  }
}
