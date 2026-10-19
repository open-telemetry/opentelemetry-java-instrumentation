/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.hasHistogramPointsSatisfyingExactly;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;

class MetricAssertionsTest {

  private static final InstrumentationScopeInfo SCOPE = InstrumentationScopeInfo.create("test");

  @Test
  void singlePoint() {
    assertThat(histogram(point(1)))
        .hasName("duration")
        .hasUnit("s")
        .satisfies(
            hasHistogramPointsSatisfyingExactly(
                point ->
                    point
                        .hasCount(1)
                        .hasSum(1)
                        .hasBucketBoundaries()
                        .hasBucketCounts(1)
                        .hasAttributes(Attributes.empty())));
  }

  @Test
  void multiplePointsInAnyOrder() {
    assertThat(singletonList(histogram(point(1), point(2))))
        .satisfiesExactly(
            hasHistogramPointsSatisfyingExactly(
                point -> point.hasCount(2).hasSum(2), point -> point.hasCount(1).hasSum(1)));
  }

  @Test
  void zeroPoints() {
    assertThat(histogram()).satisfies(hasHistogramPointsSatisfyingExactly());
  }

  @Test
  void missingPoint() {
    assertThatThrownBy(
            () ->
                assertThat(histogram(point(1)))
                    .satisfies(
                        hasHistogramPointsSatisfyingExactly(
                            point -> point.hasCount(1), point -> point.hasCount(2))))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void extraPoint() {
    assertThatThrownBy(
            () ->
                assertThat(histogram(point(1), point(2)))
                    .satisfies(hasHistogramPointsSatisfyingExactly(point -> point.hasCount(1))))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void wrongMetricType() {
    MetricData gauge =
        ImmutableMetricData.createLongGauge(
            Resource.empty(), SCOPE, "gauge", "", "", ImmutableGaugeData.empty());

    assertThatThrownBy(() -> assertThat(gauge).satisfies(hasHistogramPointsSatisfyingExactly()))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void failedPointAssertion() {
    assertThatThrownBy(
            () ->
                assertThat(histogram(point(1)))
                    .satisfies(hasHistogramPointsSatisfyingExactly(point -> point.hasCount(999))))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("to satisfy all the consumers in any order");
  }

  private static MetricData histogram(HistogramPointData... points) {
    return ImmutableMetricData.createDoubleHistogram(
        Resource.empty(),
        SCOPE,
        "duration",
        "",
        "s",
        ImmutableHistogramData.create(AggregationTemporality.CUMULATIVE, asList(points)));
  }

  private static HistogramPointData point(long count) {
    return ImmutableHistogramPointData.create(
        0,
        1,
        Attributes.empty(),
        (double) count,
        false,
        0,
        false,
        0,
        emptyList(),
        singletonList(count));
  }
}
