/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractDistributionSummaryHistogramGaugesTest {

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "1"),
                                                    equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point
                                                .hasValue(2)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "10"),
                                                    equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point
                                                .hasValue(3)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "100"),
                                                    equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point
                                                .hasValue(4)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "1000"),
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("phi"), "0.5"),
                                                equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("phi"), "0.95"),
                                                equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("phi"), "0.99"),
                                                equalTo(stringKey("tag"), "value"))))));
  }
}
