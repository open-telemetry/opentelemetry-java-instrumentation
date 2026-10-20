/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import java.util.function.Consumer;

/** Assertions for messaging metrics. */
public final class MessagingMetricsAssertions {

  /** Asserts that the named counter has a single point with the given value and attributes. */
  public static void assertCounter(
      InstrumentationExtension testing,
      String instrumentationName,
      String metricName,
      long value,
      Attributes attributes) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        metricName,
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metricData ->
                        longSum(
                                metric -> {},
                                point -> point.hasValue(value).hasAttributes(attributes))
                            .accept(assertThat(metricData))));
  }

  /** Asserts that the named histogram has one point per given attribute set. */
  public static void assertHistogram(
      InstrumentationExtension testing,
      String instrumentationName,
      String metricName,
      Attributes... attributes) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        metricName,
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metricData ->
                        histogram(metric -> {}, histogramPointAssertions(attributes))
                            .accept(assertThat(metricData))));
  }

  /** Asserts that the given instrumentation did not record the named metric. */
  public static void assertNoMetric(
      InstrumentationExtension testing, String instrumentationName, String metricName) {
    assertThat(testing.metrics())
        .noneMatch(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(instrumentationName)
                    && metric.getName().equals(metricName));
  }

  // This private array is populated only with consumers for histogram points.
  @SuppressWarnings("unchecked")
  private static Consumer<HistogramPointAssert>[] histogramPointAssertions(
      Attributes... attributes) {
    Consumer<HistogramPointAssert>[] assertions =
        (Consumer<HistogramPointAssert>[]) new Consumer<?>[attributes.length];
    for (int i = 0; i < attributes.length; i++) {
      Attributes expectedAttributes = attributes[i];
      assertions[i] = point -> point.hasCount(1).hasAttributes(expectedAttributes);
    }
    return assertions;
  }

  private MessagingMetricsAssertions() {}
}
