/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio.v6_6;

class ExperimentalTestHelper {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.twilio.experimental-span-attributes");

  static String experimental(String value) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}
