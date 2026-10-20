/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentationTestRunnerTest {

  @BeforeEach
  void setAwaitilityDefaults() {
    Awaitility.setDefaultPollDelay(Duration.ZERO);
    Awaitility.setDefaultPollInterval(Duration.ofMillis(1));
    Awaitility.setDefaultTimeout(Duration.ofMillis(20));
  }

  @AfterEach
  void resetAwaitilityDefaults() {
    Awaitility.reset();
  }

  @Test
  void matchesAllMetricsInScopeInAnyOrderAndIgnoresOtherScopes() {
    MetricData duration = histogramData("test", "duration", "s", point(2), point(1));
    MetricData gauge =
        ImmutableMetricData.createLongGauge(
            Resource.empty(),
            InstrumentationScopeInfo.create("test"),
            "request.count",
            "",
            "",
            ImmutableGaugeData.empty());
    MetricData unrelatedScope = histogramData("other", "unrelated", "s", point(3));

    runner(asList(duration, gauge, unrelatedScope))
        .waitAndAssertMetricsExactly(
            "test",
            metric -> metric.hasName("request.count"),
            histogram(
                metric -> metric.hasName("duration").hasUnit("s"),
                pointAssert -> pointAssert.hasCount(1).hasSum(1),
                pointAssert -> pointAssert.hasCount(1).hasSum(2)));
  }

  @Test
  void rejectsMissingMetrics() {
    InstrumentationTestRunner runner =
        runner(singletonList(histogramData("test", "duration", "s", point(1))));

    assertThatThrownBy(
            () ->
                runner.waitAndAssertMetricsExactly(
                    "test",
                    metric -> metric.hasName("duration"),
                    metric -> metric.hasName("request.count")))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void rejectsExtraAndDuplicateMetricsEvenWhenTheirAssertionsFail() {
    MetricData duration = histogramData("test", "duration", "s", point(1));
    MetricData extra = histogramData("test", "extra", "ms", point(2));
    MetricData duplicateWithWrongMetadata = histogramData("test", "duration", "ms", point(3));

    for (List<MetricData> metrics :
        asList(asList(duration, extra), asList(duration, duplicateWithWrongMetadata))) {
      assertThatThrownBy(
              () ->
                  runner(metrics)
                      .waitAndAssertMetricsExactly(
                          "test",
                          histogram(
                              metric -> metric.hasName("duration").hasUnit("s"),
                              pointAssert -> pointAssert.hasCount(1))))
          .isInstanceOf(AssertionError.class);
    }
  }

  @Test
  void histogramAssertionRejectsExtraPoints() {
    InstrumentationTestRunner runner =
        runner(singletonList(histogramData("test", "duration", "s", point(1), point(2))));

    assertThatThrownBy(
            () ->
                runner.waitAndAssertMetricsExactly(
                    "test",
                    histogram(
                        metric -> metric.hasName("duration"),
                        pointAssert -> pointAssert.hasCount(1).hasSum(1))))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void histogramAssertionRejectsNonHistogramMetricType() {
    MetricData gauge =
        ImmutableMetricData.createLongGauge(
            Resource.empty(),
            InstrumentationScopeInfo.create("test"),
            "duration",
            "",
            "",
            ImmutableGaugeData.empty());

    assertThatThrownBy(
            () ->
                histogram(
                        metric -> metric.hasName("duration"),
                        pointAssert -> pointAssert.hasCount(1))
                    .accept(assertThat(gauge)))
        .isInstanceOf(AssertionError.class);
  }

  private static InstrumentationTestRunner runner(List<MetricData> metrics) {
    return new FixedMetricsTestRunner(metrics);
  }

  private static MetricData histogramData(
      String scopeName, String metricName, String unit, HistogramPointData... points) {
    return ImmutableMetricData.createDoubleHistogram(
        Resource.empty(),
        InstrumentationScopeInfo.create(scopeName),
        metricName,
        "",
        unit,
        ImmutableHistogramData.create(AggregationTemporality.CUMULATIVE, asList(points)));
  }

  private static HistogramPointData point(long sum) {
    return ImmutableHistogramPointData.create(
        0, 1, Attributes.empty(), (double) sum, false, 0, false, 0, emptyList(), singletonList(1L));
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
