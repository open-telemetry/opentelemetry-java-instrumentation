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
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramBuckets;
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
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MetricAssertionsTest {

  private static final InstrumentationScopeInfo SCOPE = InstrumentationScopeInfo.create("test");
  private static final List<LongPointData> LONG_POINTS =
      asList(
          ImmutableLongPointData.create(0, 1, Attributes.empty(), 1),
          ImmutableLongPointData.create(0, 1, Attributes.empty(), 2));
  private static final List<DoublePointData> DOUBLE_POINTS =
      asList(
          ImmutableDoublePointData.create(0, 1, Attributes.empty(), 1.5),
          ImmutableDoublePointData.create(0, 1, Attributes.empty(), 2.5));
  private static final MetricData LONG_SUM =
      ImmutableMetricData.createLongSum(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableSumData.create(true, AggregationTemporality.CUMULATIVE, LONG_POINTS));
  private static final MetricData DOUBLE_SUM =
      ImmutableMetricData.createDoubleSum(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableSumData.create(false, AggregationTemporality.DELTA, DOUBLE_POINTS));
  private static final MetricData LONG_GAUGE =
      ImmutableMetricData.createLongGauge(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableGaugeData.create(LONG_POINTS));
  private static final MetricData DOUBLE_GAUGE =
      ImmutableMetricData.createDoubleGauge(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableGaugeData.create(DOUBLE_POINTS));
  private static final MetricData HISTOGRAM =
      ImmutableMetricData.createDoubleHistogram(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableHistogramData.create(
              AggregationTemporality.CUMULATIVE,
              asList(
                  ImmutableHistogramPointData.create(
                      0,
                      1,
                      Attributes.empty(),
                      1.5,
                      true,
                      1.5,
                      true,
                      1.5,
                      emptyList(),
                      singletonList(1L)),
                  ImmutableHistogramPointData.create(
                      0,
                      1,
                      Attributes.empty(),
                      5,
                      true,
                      2.5,
                      true,
                      2.5,
                      emptyList(),
                      singletonList(2L)))));
  private static final ExponentialHistogramBuckets EMPTY_BUCKETS =
      ImmutableExponentialHistogramBuckets.create(0, 0, emptyList());
  private static final MetricData EXPONENTIAL_HISTOGRAM =
      ImmutableMetricData.createExponentialHistogram(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableExponentialHistogramData.create(
              AggregationTemporality.CUMULATIVE,
              asList(
                  ImmutableExponentialHistogramPointData.create(
                      0,
                      0,
                      1,
                      true,
                      0,
                      true,
                      0,
                      EMPTY_BUCKETS,
                      EMPTY_BUCKETS,
                      0,
                      1,
                      Attributes.empty(),
                      emptyList()),
                  ImmutableExponentialHistogramPointData.create(
                      0,
                      0,
                      2,
                      true,
                      0,
                      true,
                      0,
                      EMPTY_BUCKETS,
                      EMPTY_BUCKETS,
                      0,
                      1,
                      Attributes.empty(),
                      emptyList()))));
  private static final MetricData SUMMARY =
      ImmutableMetricData.createDoubleSummary(
          Resource.empty(),
          SCOPE,
          "metric",
          "description",
          "s",
          ImmutableSummaryData.create(
              asList(
                  ImmutableSummaryPointData.create(0, 1, Attributes.empty(), 1, 1.5, emptyList()),
                  ImmutableSummaryPointData.create(0, 1, Attributes.empty(), 2, 5, emptyList()))));

  @ParameterizedTest
  @MethodSource("matchingAssertions")
  void composesMetadataAndTypedPointsInAnyOrder(MetricData data, Consumer<MetricAssert> assertion) {
    assertion.accept(assertThat(data));
  }

  private static Stream<Arguments> matchingAssertions() {
    return Stream.of(
        Arguments.of(
            HISTOGRAM,
            histogram(
                MetricAssertionsTest::assertMetadata,
                point -> point.hasCount(2).hasSum(5),
                point -> point.hasCount(1).hasSum(1.5))),
        Arguments.of(
            LONG_SUM,
            longSum(
                metric -> {
                  assertMetadata(metric);
                  metric.hasLongSumSatisfying(sum -> sum.isMonotonic().isCumulative());
                },
                point -> point.hasValue(2),
                point -> point.hasValue(1))),
        Arguments.of(
            DOUBLE_SUM,
            doubleSum(
                metric -> {
                  assertMetadata(metric);
                  metric.hasDoubleSumSatisfying(sum -> sum.isNotMonotonic().isDelta());
                },
                point -> point.hasValue(2.5),
                point -> point.hasValue(1.5))),
        Arguments.of(
            LONG_GAUGE,
            longGauge(
                MetricAssertionsTest::assertMetadata,
                point -> point.hasValue(2),
                point -> point.hasValue(1))),
        Arguments.of(
            DOUBLE_GAUGE,
            doubleGauge(
                MetricAssertionsTest::assertMetadata,
                point -> point.hasValue(2.5),
                point -> point.hasValue(1.5))),
        Arguments.of(
            EXPONENTIAL_HISTOGRAM,
            exponentialHistogram(
                MetricAssertionsTest::assertMetadata,
                point -> point.hasCount(2).hasZeroCount(2),
                point -> point.hasCount(1).hasZeroCount(1))),
        Arguments.of(
            SUMMARY,
            summary(
                MetricAssertionsTest::assertMetadata,
                point -> point.hasCount(2).hasSum(5),
                point -> point.hasCount(1).hasSum(1.5))));
  }

  @ParameterizedTest
  @MethodSource("mismatchedHistogramAssertions")
  void retainsMetadataAndPointFailures(Consumer<MetricAssert> assertion) {
    assertThatThrownBy(() -> assertion.accept(assertThat(HISTOGRAM)))
        .isInstanceOf(AssertionError.class);
  }

  private static Stream<Consumer<MetricAssert>> mismatchedHistogramAssertions() {
    return Stream.of(
        histogram(
            metric -> metric.hasName("other"),
            point -> point.hasCount(1),
            point -> point.hasCount(2)),
        histogram(
            metric -> metric.hasDescription("other"),
            point -> point.hasCount(1),
            point -> point.hasCount(2)),
        histogram(
            metric -> metric.hasUnit("other"),
            point -> point.hasCount(1),
            point -> point.hasCount(2)),
        histogram(
            MetricAssertionsTest::assertMetadata,
            point -> point.hasSum(99),
            point -> point.hasCount(2)),
        histogram(MetricAssertionsTest::assertMetadata, point -> point.hasCount(1)),
        histogram(
            MetricAssertionsTest::assertMetadata,
            point -> point.hasCount(1),
            point -> point.hasCount(2),
            point -> point.hasCount(3)));
  }

  @Test
  void rejectsIncompatibleMetricTypes() {
    assertThatThrownBy(
            () ->
                longGauge(MetricAssertionsTest::assertMetadata, point -> point.hasValue(1))
                    .accept(assertThat(HISTOGRAM)))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                doubleSum(MetricAssertionsTest::assertMetadata, point -> point.hasValue(1))
                    .accept(assertThat(LONG_SUM)))
        .isInstanceOf(AssertionError.class);
  }

  @ParameterizedTest
  @MethodSource("mismatchedSumAssertions")
  void retainsSumLevelFailures(MetricData data, Consumer<MetricAssert> assertion) {
    assertThatThrownBy(() -> assertion.accept(assertThat(data))).isInstanceOf(AssertionError.class);
  }

  private static Stream<Arguments> mismatchedSumAssertions() {
    return Stream.of(
        Arguments.of(
            LONG_SUM,
            longSum(
                metric -> metric.hasLongSumSatisfying(sum -> sum.isNotMonotonic()),
                point -> point.hasValue(2),
                point -> point.hasValue(1))),
        Arguments.of(
            LONG_SUM,
            longSum(
                metric -> metric.hasLongSumSatisfying(sum -> sum.isDelta()),
                point -> point.hasValue(2),
                point -> point.hasValue(1))),
        Arguments.of(
            DOUBLE_SUM,
            doubleSum(
                metric -> metric.hasDoubleSumSatisfying(sum -> sum.isMonotonic()),
                point -> point.hasValue(2.5),
                point -> point.hasValue(1.5))),
        Arguments.of(
            DOUBLE_SUM,
            doubleSum(
                metric -> metric.hasDoubleSumSatisfying(sum -> sum.isCumulative()),
                point -> point.hasValue(2.5),
                point -> point.hasValue(1.5))));
  }

  private static void assertMetadata(MetricAssert metric) {
    metric.hasName("metric").hasDescription("description").hasUnit("s");
  }
}
