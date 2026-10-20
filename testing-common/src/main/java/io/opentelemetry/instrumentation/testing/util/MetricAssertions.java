/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.function.Consumer;

/** Composable assertions on metrics. */
public final class MetricAssertions {

  /**
   * Returns an assertion that validates metric metadata and exactly matches the histogram points,
   * in any order.
   *
   * <pre>{@code
   * testing.waitAndAssertMetricsExactly(
   *     "test",
   *     histogram(
   *         metric -> metric.hasName("duration").hasUnit("s"),
   *         point -> point.hasCount(1)));
   * }</pre>
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> histogram(
      Consumer<MetricAssert> metricAssertion, Consumer<HistogramPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasHistogramSatisfying(histogram -> histogram.hasPointsSatisfying(pointAssertions));
    };
  }

  private MetricAssertions() {}
}
