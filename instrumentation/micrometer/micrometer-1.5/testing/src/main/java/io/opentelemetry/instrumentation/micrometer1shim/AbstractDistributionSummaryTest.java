/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import static io.opentelemetry.instrumentation.micrometer1shim.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractDistributionSummaryTest {

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
                                                .hasAttributes(attributeEntry("tag", "value"))))));
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
  void testMicrometerHistogram() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummary")
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
                                        points ->
                                            points
                                                .hasSum(555.5)
                                                .hasCount(4)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
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
                                                .hasValue(500)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testSummary.histogram",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributes(
                                                    attributeEntry("le", "1"),
                                                    attributeEntry("tag", "value")),
                                        point ->
                                            point
                                                .hasValue(2)
                                                .hasAttributes(
                                                    attributeEntry("le", "10"),
                                                    attributeEntry("tag", "value")),
                                        point ->
                                            point
                                                .hasValue(3)
                                                .hasAttributes(
                                                    attributeEntry("le", "100"),
                                                    attributeEntry("tag", "value")),
                                        point ->
                                            point
                                                .hasValue(4)
                                                .hasAttributes(
                                                    attributeEntry("le", "1000"),
                                                    attributeEntry("tag", "value"))))));
  }

  @Test
  void testMicrometerPercentiles() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummary")
            .description("This is a test distribution summary")
            .baseUnit("things")
            .tags("tag", "value")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(Metrics.globalRegistry);

    // when
    summary.record(50);
    summary.record(100);

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
                                                .hasSum(150)
                                                .hasCount(2)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
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
                                                .hasValue(100)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testSummary.percentile",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("phi", "0.5"),
                                                attributeEntry("tag", "value")),
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("phi", "0.95"),
                                                attributeEntry("tag", "value")),
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("phi", "0.99"),
                                                attributeEntry("tag", "value"))))));
  }
}
