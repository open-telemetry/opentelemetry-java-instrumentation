/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class InstrumentationTestRunnerTest {

  @Test
  void requiresExactlyOneMetricBeforeAssertions() {
    MetricData metric =
        ImmutableMetricData.createLongGauge(
            Resource.empty(),
            InstrumentationScopeInfo.create("test"),
            "duration",
            "",
            "",
            ImmutableGaugeData.empty());
    InstrumentationTestRunner runner = new FixedMetricsTestRunner(asList(metric, metric));
    AtomicBoolean metricAssertionCalled = new AtomicBoolean();
    AtomicBoolean pointAssertionCalled = new AtomicBoolean();

    Awaitility.setDefaultPollDelay(Duration.ZERO);
    Awaitility.setDefaultPollInterval(Duration.ofMillis(1));
    Awaitility.setDefaultTimeout(Duration.ofMillis(10));
    try {
      assertThatThrownBy(
              () ->
                  runner.waitAndAssertHistogramPoints(
                      "test",
                      "duration",
                      metricAssert -> {
                        metricAssertionCalled.set(true);
                        throw new IllegalStateException();
                      },
                      pointAssert -> pointAssertionCalled.set(true)))
          .isInstanceOf(AssertionError.class);
    } finally {
      Awaitility.reset();
    }

    assertThat(metricAssertionCalled).isFalse();
    assertThat(pointAssertionCalled).isFalse();
  }

  @Test
  void anyHistogramMustMatchMetadataAndPointsOnTheSameMetric() {
    MetricData wrongMetadataAndPoints = histogram("test", "duration", "other", 2);
    MetricData rightMetadataAndWrongPoints = histogram("test", "duration", "matching", 2);
    MetricData wrongMetadataAndRightPoints = histogram("test", "duration", "other", 1);
    MetricData expected = histogram("test", "duration", "matching", 1);
    MetricData otherScope = histogram("other", "duration", "matching", 1);
    MetricData otherName = histogram("test", "other", "matching", 1);

    InstrumentationTestRunner matchingRunner =
        new FixedMetricsTestRunner(
            asList(
                wrongMetadataAndPoints,
                rightMetadataAndWrongPoints,
                wrongMetadataAndRightPoints,
                otherScope,
                otherName,
                expected));

    InstrumentationTestRunner nonMatchingRunner =
        new FixedMetricsTestRunner(
            asList(rightMetadataAndWrongPoints, wrongMetadataAndRightPoints));
    InstrumentationTestRunner noMatchingMetricRunner =
        new FixedMetricsTestRunner(asList(otherScope, otherName));

    Awaitility.setDefaultPollDelay(Duration.ZERO);
    Awaitility.setDefaultPollInterval(Duration.ofMillis(1));
    Awaitility.setDefaultTimeout(Duration.ofMillis(10));
    try {
      matchingRunner.waitAndAssertAnyHistogramPoints(
          "test",
          "duration",
          metric -> metric.hasDescription("matching"),
          point -> point.hasCount(1));

      assertThatThrownBy(
              () ->
                  nonMatchingRunner.waitAndAssertAnyHistogramPoints(
                      "test",
                      "duration",
                      metric -> metric.hasDescription("matching"),
                      point -> point.hasCount(1)))
          .isInstanceOf(AssertionError.class);

      assertThatThrownBy(
              () ->
                  noMatchingMetricRunner.waitAndAssertAnyHistogramPoints(
                      "test",
                      "duration",
                      metric -> metric.hasDescription("matching"),
                      point -> point.hasCount(1)))
          .isInstanceOf(AssertionError.class);
    } finally {
      Awaitility.reset();
    }
  }

  private static MetricData histogram(
      String instrumentationName, String metricName, String description, long pointCount) {
    return ImmutableMetricData.createDoubleHistogram(
        Resource.empty(),
        InstrumentationScopeInfo.create(instrumentationName),
        metricName,
        description,
        "s",
        ImmutableHistogramData.create(
            AggregationTemporality.CUMULATIVE,
            singletonList(
                ImmutableHistogramPointData.create(
                    0,
                    1,
                    Attributes.empty(),
                    (double) pointCount,
                    false,
                    0,
                    false,
                    0,
                    emptyList(),
                    singletonList(pointCount)))));
  }

  private static class FixedMetricsTestRunner extends InstrumentationTestRunner {

    private final List<MetricData> metrics;

    private FixedMetricsTestRunner(List<MetricData> metrics) {
      super(OpenTelemetry.noop());
      this.metrics = metrics;
    }

    @Override
    public void beforeTestClass() {}

    @Override
    public void afterTestClass() throws IOException {}

    @Override
    public void clearAllExportedData() {}

    @Override
    public OpenTelemetry getOpenTelemetry() {
      return OpenTelemetry.noop();
    }

    @Override
    public List<SpanData> getExportedSpans() {
      return emptyList();
    }

    @Override
    public List<MetricData> getExportedMetrics() {
      return metrics;
    }

    @Override
    public List<LogRecordData> getExportedLogRecords() {
      return emptyList();
    }

    @Override
    public boolean forceFlushCalled() {
      return false;
    }
  }
}
