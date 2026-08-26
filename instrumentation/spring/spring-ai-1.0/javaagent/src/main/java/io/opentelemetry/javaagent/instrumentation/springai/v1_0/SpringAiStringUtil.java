/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import javax.annotation.Nullable;

class SpringAiStringUtil {

  static int safeEndIndex(String value, int maxLength) {
    int end = Math.min(value.length(), Math.max(0, maxLength));
    if (end < value.length()
        && end > 0
        && Character.isHighSurrogate(value.charAt(end - 1))
        && Character.isLowSurrogate(value.charAt(end))) {
      end--;
    }
    return end;
  }

  @Nullable
  static String truncate(@Nullable String value, int maxLength) {
    if (value == null || maxLength < 0 || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, safeEndIndex(value, maxLength));
  }

  private SpringAiStringUtil() {}
}
