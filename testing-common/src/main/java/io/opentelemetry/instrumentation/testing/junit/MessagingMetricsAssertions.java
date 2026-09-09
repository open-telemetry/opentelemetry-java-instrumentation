/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.HashSet;
import java.util.Set;

/** Assertions for the messaging metrics defined by the v1.43 semantic conventions. */
public final class MessagingMetricsAssertions {

  private static final Set<String> STABLE_METRICS =
      unmodifiableSet(
          new HashSet<>(
              asList(
                  "messaging.client.operation.duration",
                  "messaging.client.sent.messages",
                  "messaging.client.consumed.messages",
                  "messaging.process.duration")));

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
            metrics.singleElement().satisfies(metric -> verifyCounter(metric, value, attributes)));
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
            metrics.singleElement().satisfies(metric -> verifyHistogram(metric, attributes)));
  }

  /** Asserts that the given instrumentation recorded none of the stable messaging metrics. */
  public static void assertNoStableMetrics(
      InstrumentationExtension testing, String instrumentationName) {
    assertThat(testing.metrics())
        .noneMatch(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(instrumentationName)
                    && STABLE_METRICS.contains(metric.getName()));
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

  private static void verifyCounter(MetricData metric, long value, Attributes attributes) {
    assertThat(metric.getLongSumData().getPoints())
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.getValue()).isEqualTo(value);
              assertThat(point.getAttributes()).isEqualTo(attributes);
            });
  }

  private static void verifyHistogram(MetricData metric, Attributes... attributes) {
    assertThat(metric.getHistogramData().getPoints())
        .allSatisfy(point -> assertThat(point.getCount()).isEqualTo(1))
        .extracting(point -> point.getAttributes())
        .containsExactlyInAnyOrder(attributes);
  }

  private MessagingMetricsAssertions() {}
}
