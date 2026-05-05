/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.common.v3_0;

class ExperimentalTestHelper {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.rxjava.experimental-span-attributes");

  static Boolean experimentalCanceled(boolean value) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}
