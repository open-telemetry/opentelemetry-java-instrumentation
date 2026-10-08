/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.assertj.core.api.AbstractIterableAssert;

final class NoMaxGaugeAssertions {

  static void assertNoMaxGauge(InstrumentationExtension testing, String name) {
    testing.waitAndAssertMetrics(INSTRUMENTATION_NAME, name, AbstractIterableAssert::isEmpty);
  }

  private NoMaxGaugeAssertions() {}
}
