/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;

class ExperimentalTest {
  private static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.camel.experimental-span-attributes";

  static String experimental(String value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }

  static OpenTelemetryAssertions.StringAssertConsumer experimental(
      OpenTelemetryAssertions.StringAssertConsumer value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }

  private ExperimentalTest() {}
}
