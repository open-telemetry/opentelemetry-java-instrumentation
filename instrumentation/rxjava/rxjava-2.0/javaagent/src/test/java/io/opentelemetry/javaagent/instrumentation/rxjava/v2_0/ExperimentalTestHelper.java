/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rxjava.v2_0;

class ExperimentalTestHelper {
  private static final boolean isEnabled =
      Boolean.getBoolean("otel.instrumentation.rxjava.experimental-span-attributes");

  static Boolean experimentalCanceled(boolean value) {
    if (isEnabled) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}
