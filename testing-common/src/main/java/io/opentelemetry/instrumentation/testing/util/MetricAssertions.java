/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import io.opentelemetry.sdk.testing.assertj.DoublePointAssert;
import io.opentelemetry.sdk.testing.assertj.ExponentialHistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import io.opentelemetry.sdk.testing.assertj.SummaryPointAssert;
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

  /** Returns an assertion that validates metadata and exactly matches long sum points. */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> longSum(
      Consumer<MetricAssert> metricAssertion, Consumer<LongPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasLongSumSatisfying(sum -> sum.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Returns an assertion that validates metadata and exactly matches double sum points. */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> doubleSum(
      Consumer<MetricAssert> metricAssertion, Consumer<DoublePointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasDoubleSumSatisfying(sum -> sum.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Returns an assertion that validates metadata and exactly matches long gauge points. */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> longGauge(
      Consumer<MetricAssert> metricAssertion, Consumer<LongPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasLongGaugeSatisfying(gauge -> gauge.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Returns an assertion that validates metadata and exactly matches double gauge points. */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> doubleGauge(
      Consumer<MetricAssert> metricAssertion, Consumer<DoublePointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasDoubleGaugeSatisfying(gauge -> gauge.hasPointsSatisfying(pointAssertions));
    };
  }

  /**
   * Returns an assertion that validates metadata and exactly matches exponential histogram points.
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> exponentialHistogram(
      Consumer<MetricAssert> metricAssertion,
      Consumer<ExponentialHistogramPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasExponentialHistogramSatisfying(
          histogram -> histogram.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Returns an assertion that validates metadata and exactly matches summary points. */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Consumer<MetricAssert> summary(
      Consumer<MetricAssert> metricAssertion, Consumer<SummaryPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasSummarySatisfying(summary -> summary.hasPointsSatisfying(pointAssertions));
    };
  }

  private MetricAssertions() {}
}
