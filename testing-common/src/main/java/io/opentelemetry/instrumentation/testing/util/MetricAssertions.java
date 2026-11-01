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

/**
 * Composes metric assertions with typed point assertions for {@code waitAndAssertMetrics}.
 *
 * <p>Each factory runs the metric callback first, then delegates type checking and exact, any-order
 * point matching to the SDK assertions. Sum-level assertions such as monotonicity and temporality
 * can be supplied in the metric callback. These helpers do not enforce exhaustive matching across
 * metrics or normalize exports; the runner's existing metric selection behavior is unchanged.
 *
 * <pre>{@code
 * testing.waitAndAssertMetrics(
 *     "io.opentelemetry.example",
 *     histogram(
 *         metric -> metric.hasName("duration").hasUnit("s"),
 *         point -> point.hasCount(1)));
 * }</pre>
 */
@SuppressWarnings("varargs")
public final class MetricAssertions {

  /** Asserts histogram metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> histogram(
      Consumer<MetricAssert> metricAssertion, Consumer<HistogramPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasHistogramSatisfying(histogram -> histogram.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Asserts long sum metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> longSum(
      Consumer<MetricAssert> metricAssertion, Consumer<LongPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasLongSumSatisfying(sum -> sum.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Asserts double sum metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> doubleSum(
      Consumer<MetricAssert> metricAssertion, Consumer<DoublePointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasDoubleSumSatisfying(sum -> sum.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Asserts long gauge metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> longGauge(
      Consumer<MetricAssert> metricAssertion, Consumer<LongPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasLongGaugeSatisfying(gauge -> gauge.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Asserts double gauge metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> doubleGauge(
      Consumer<MetricAssert> metricAssertion, Consumer<DoublePointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasDoubleGaugeSatisfying(gauge -> gauge.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Asserts exponential histogram metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> exponentialHistogram(
      Consumer<MetricAssert> metricAssertion,
      Consumer<ExponentialHistogramPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasExponentialHistogramSatisfying(
          histogram -> histogram.hasPointsSatisfying(pointAssertions));
    };
  }

  /** Asserts summary metadata and exactly the supplied points, in any order. */
  @SafeVarargs
  public static Consumer<MetricAssert> summary(
      Consumer<MetricAssert> metricAssertion, Consumer<SummaryPointAssert>... pointAssertions) {
    return metric -> {
      metricAssertion.accept(metric);
      metric.hasSummarySatisfying(summary -> summary.hasPointsSatisfying(pointAssertions));
    };
  }

  private MetricAssertions() {}
}
