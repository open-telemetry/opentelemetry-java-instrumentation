/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractIterableAssert;

/**
 * The micrometer bridge emits a decaying {@code <name>.max} gauge alongside the histogram for
 * {@code Timer} and {@code DistributionSummary}. That gauge is not emitted when the v3 preview is
 * enabled, and it will be removed in 3.0.
 */
final class MaxGaugeAssertions {

  /**
   * Asserts that the {@code <name>.max} gauge satisfies {@code assertion}, or that it is not
   * emitted at all when the v3 preview is enabled.
   */
  static void assertMaxGauge(
      InstrumentationExtension testing, String name, Consumer<MetricAssert> assertion) {
    if (SemconvStability.v3Preview()) {
      testing.waitAndAssertMetrics(INSTRUMENTATION_NAME, name, AbstractIterableAssert::isEmpty);
    } else {
      testing.waitAndAssertMetrics(INSTRUMENTATION_NAME, assertion);
    }
  }

  private MaxGaugeAssertions() {}
}
