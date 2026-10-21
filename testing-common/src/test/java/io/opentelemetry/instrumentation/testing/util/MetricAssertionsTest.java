/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.doubleGauge;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.doubleSum;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.exponentialHistogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longGauge;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.summary;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.ValueAtQuantile;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSummaryData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSummaryPointData;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;

class MetricAssertionsTest {

  private static final Attributes ATTRIBUTES = Attributes.empty();
  private static final Resource RESOURCE = Resource.empty();
  private static final InstrumentationScopeInfo SCOPE =
      InstrumentationScopeInfo.create("test.metrics");

  @Test
  void supportsHistogramHelper() {
    histogram(metric -> metric.hasName("histogram"), point -> point.hasCount(1))
        .accept(assertThat(histogramMetric()));
  }

  @Test
  void supportsLongSumHelper() {
    longSum(metric -> metric.hasName("long.sum"), point -> point.hasValue(1))
        .accept(assertThat(longSumMetric()));
  }

  @Test
  void supportsDoubleSumHelper() {
    doubleSum(metric -> metric.hasName("double.sum"), point -> point.hasValue(1))
        .accept(assertThat(doubleSumMetric()));
  }

  @Test
  void supportsLongGaugeHelper() {
    longGauge(metric -> metric.hasName("long.gauge"), point -> point.hasValue(1))
        .accept(assertThat(longGaugeMetric()));
  }

  @Test
  void supportsDoubleGaugeHelper() {
    doubleGauge(metric -> metric.hasName("double.gauge"), point -> point.hasValue(1))
        .accept(assertThat(doubleGaugeMetric()));
  }

  @Test
  void supportsExponentialHistogramHelper() {
    exponentialHistogram(
            metric -> metric.hasName("exponential.histogram"), point -> point.hasCount(1))
        .accept(assertThat(exponentialHistogramMetric()));
  }

  @Test
  void supportsSummaryHelper() {
    summary(metric -> metric.hasName("summary"), point -> point.hasCount(1))
        .accept(assertThat(summaryMetric()));
  }

  private static MetricData histogramMetric() {
    return ImmutableMetricData.createDoubleHistogram(
        RESOURCE,
        SCOPE,
        "histogram",
        "",
        "",
        ImmutableHistogramData.create(
            AggregationTemporality.CUMULATIVE,
            singletonList(
                ImmutableHistogramPointData.create(
                    1, 2, ATTRIBUTES, 1, false, 0, false, 0, emptyList(), singletonList(1L)))));
  }

  private static MetricData longSumMetric() {
    return ImmutableMetricData.createLongSum(
        RESOURCE,
        SCOPE,
        "long.sum",
        "",
        "",
        ImmutableSumData.create(
            true,
            AggregationTemporality.CUMULATIVE,
            singletonList(ImmutableLongPointData.create(1, 2, ATTRIBUTES, 1))));
  }

  private static MetricData doubleSumMetric() {
    return ImmutableMetricData.createDoubleSum(
        RESOURCE,
        SCOPE,
        "double.sum",
        "",
        "",
        ImmutableSumData.create(
            true,
            AggregationTemporality.CUMULATIVE,
            singletonList(ImmutableDoublePointData.create(1, 2, ATTRIBUTES, 1))));
  }

  private static MetricData longGaugeMetric() {
    return ImmutableMetricData.createLongGauge(
        RESOURCE,
        SCOPE,
        "long.gauge",
        "",
        "",
        ImmutableGaugeData.create(
            singletonList(ImmutableLongPointData.create(1, 2, ATTRIBUTES, 1))));
  }

  private static MetricData doubleGaugeMetric() {
    return ImmutableMetricData.createDoubleGauge(
        RESOURCE,
        SCOPE,
        "double.gauge",
        "",
        "",
        ImmutableGaugeData.create(
            singletonList(ImmutableDoublePointData.create(1, 2, ATTRIBUTES, 1))));
  }

  private static MetricData exponentialHistogramMetric() {
    ExponentialHistogramBuckets buckets =
        ExponentialHistogramBuckets.create(0, 0, singletonList(1L));
    return ImmutableMetricData.createExponentialHistogram(
        RESOURCE,
        SCOPE,
        "exponential.histogram",
        "",
        "",
        ImmutableExponentialHistogramData.create(
            AggregationTemporality.CUMULATIVE,
            singletonList(
                ImmutableExponentialHistogramPointData.create(
                    0,
                    1,
                    0,
                    false,
                    0,
                    false,
                    0,
                    buckets,
                    ExponentialHistogramBuckets.create(0, 0, emptyList()),
                    1,
                    2,
                    ATTRIBUTES,
                    emptyList()))));
  }

  private static MetricData summaryMetric() {
    return ImmutableMetricData.createDoubleSummary(
        RESOURCE,
        SCOPE,
        "summary",
        "",
        "",
        ImmutableSummaryData.create(
            singletonList(
                ImmutableSummaryPointData.create(
                    1, 2, ATTRIBUTES, 1, 1, singletonList(ValueAtQuantile.create(0.5, 1))))));
  }
}
