/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import javax.annotation.Nullable;

public final class JedisServerError {
  @Nullable
  public static String errorType(@Nullable String reply) {
    if (reply == null) {
      return null;
    }
    int start = 0;
    while (start < reply.length() && reply.charAt(start) == ' ') {
      start++;
    }
    int end = start;
    while (end < reply.length()) {
      char character = reply.charAt(end);
      if (character == ' ' || character == '\r' || character == '\n') {
        break;
      }
      end++;
    }
    return start == end ? null : reply.substring(start, end);
  }

  private JedisServerError() {}
}
