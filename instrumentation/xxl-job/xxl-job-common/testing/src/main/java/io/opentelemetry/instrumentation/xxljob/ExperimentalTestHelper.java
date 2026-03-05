/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.xxljob;

import javax.annotation.Nullable;

public class ExperimentalTestHelper {
  private static final boolean isEnabled =
      Boolean.getBoolean("otel.instrumentation.xxl-job.experimental-span-attributes");

  @Nullable
  public static String experimental(String value) {
    if (isEnabled) {
      return value;
    }
    return null;
  }

  @Nullable
  public static Long experimental(long value) {
    if (isEnabled) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}
