/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix.v1_4;

class ExperimentalTestHelper {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.hystrix.experimental-span-attributes");

  static <T> T experimental(T value) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}
