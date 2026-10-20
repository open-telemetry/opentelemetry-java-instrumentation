/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisCommandExecutionException;
import javax.annotation.Nullable;

final class LettuceRedisErrorType {

  private LettuceRedisErrorType() {}

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (!(error instanceof RedisCommandExecutionException)) {
      return null;
    }

    String message = error.getMessage();
    if (message == null || message.isEmpty()) {
      return null;
    }

    int separator = message.indexOf(' ');
    String errorType = separator == -1 ? message : message.substring(0, separator);
    if (errorType.isEmpty()) {
      return null;
    }
    return isRedisErrorType(errorType) ? errorType : null;
  }

  private static boolean isRedisErrorType(String errorType) {
    if (errorType.length() > 64 || errorType.charAt(0) < 'A' || errorType.charAt(0) > 'Z') {
      return false;
    }
    for (int i = 1; i < errorType.length(); i++) {
      char c = errorType.charAt(i);
      if ((c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_' && c != '-') {
        return false;
      }
    }
    return true;
  }
}
