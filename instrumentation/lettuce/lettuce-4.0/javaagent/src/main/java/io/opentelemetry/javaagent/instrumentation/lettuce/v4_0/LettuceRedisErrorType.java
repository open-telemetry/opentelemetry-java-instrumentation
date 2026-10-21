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

    int messageEnd = message.indexOf("\r\n");
    if (messageEnd == -1) {
      messageEnd = message.length();
    }

    int tokenStart = 0;
    while (tokenStart < messageEnd && message.charAt(tokenStart) == ' ') {
      tokenStart++;
    }
    if (tokenStart == messageEnd) {
      return null;
    }

    int tokenEnd = message.indexOf(' ', tokenStart);
    if (tokenEnd == -1 || tokenEnd > messageEnd) {
      tokenEnd = messageEnd;
    }
    return message.substring(tokenStart, tokenEnd);
  }
}
