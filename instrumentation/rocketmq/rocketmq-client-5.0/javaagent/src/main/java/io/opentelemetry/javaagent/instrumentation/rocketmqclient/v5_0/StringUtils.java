/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import javax.annotation.Nullable;

final class StringUtils {

  @Nullable
  static String emptyToNull(@Nullable String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private StringUtils() {}
}
