/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractDistributionSummaryTest {

  static final double[] NO_BUCKETS = new double[0];

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testMicrometerDistributionSummary() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummary")
            .description("This is a test distribution summary")
            .baseUnit("things")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    summary.record(1);
    summary.record(2);
    summary.record(4);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testSummary",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test distribution summary")
                            .hasUnit("things")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(7)
                                                .hasCount(3)
                                                .hasAttributes(attributeEntry("tag", "value"))
                                                .hasBucketBoundaries(NO_BUCKETS)))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testSummary.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test distribution summary")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(4)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // micrometer gauge histogram is not emitted
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testSummary.histogram", AbstractIterableAssert::isEmpty);

    // when
    Metrics.globalRegistry.remove(summary);

    // then
    // Histogram is synchronous and returns previous value after removal, max is asynchronous and is
    // removed completely.
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testSummary",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(7)
                                                .hasCount(3)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
  }

  @Test
  void testMicrometerDistributionSummaryWithCustomBuckets() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummaryWithCustomBuckets")
            .description("This is a test distribution summary")
            .baseUnit("things")
            .tags("tag", "value")
            .serviceLevelObjectives(1, 10, 100, 1000)
            .distributionStatisticBufferLength(10)
            .register(Metrics.globalRegistry);

    // when
    summary.record(0.5);
    summary.record(5);
    summary.record(50);
    summary.record(500);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testSummaryWithCustomBuckets",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test distribution summary")
                            .hasUnit("things")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        points ->
                                            points
                                                .hasSum(555.5)
                                                .hasCount(4)
                                                .hasAttributes(attributeEntry("tag", "value"))
                                                .hasBucketBoundaries(1, 10, 100, 1000)
                                                .hasBucketCounts(1, 1, 1, 1, 0)))));
  }
}
