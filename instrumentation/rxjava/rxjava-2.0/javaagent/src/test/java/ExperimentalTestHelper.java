/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import javax.annotation.Nullable;

class ExperimentalTestHelper {
  private static final boolean isEnabled =
      Boolean.getBoolean("otel.instrumentation.rxjava.experimental-span-attributes");

  @Nullable
  static Boolean experimentalCanceled(boolean value) {
    if (isEnabled) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}
