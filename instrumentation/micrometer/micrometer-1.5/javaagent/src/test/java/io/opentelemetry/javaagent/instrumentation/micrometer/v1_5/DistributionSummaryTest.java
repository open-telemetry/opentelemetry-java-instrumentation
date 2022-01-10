/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DistributionSummaryTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testDistributionSummary() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummary")
            .description("This is a test distribution summary")
            .baseUnit("things")
            .scale(2.0)
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    summary.record(21);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testSummary",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test distribution summary")
                        .hasUnit("things")
                        .hasDoubleHistogram()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasSum(42)
                                    .hasCount(1)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(summary);
    summary.record(6);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testSummary",
        metrics ->
            metrics.allSatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleHistogram()
                        .points()
                        .noneSatisfy(point -> assertThat(point).hasSum(54).hasCount(2))));
  }

  @Test
  void testMicrometerHistogram() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummaryHistogram")
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
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testSummaryHistogram.histogram",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point).hasValue(1).attributes().containsEntry("le", "1"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(2)
                                    .attributes()
                                    .containsEntry("le", "10"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(3)
                                    .attributes()
                                    .containsEntry("le", "100"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(4)
                                    .attributes()
                                    .containsEntry("le", "1000"))));
  }

  @Test
  void testMicrometerPercentiles() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummaryPercentiles")
            .description("This is a test distribution summary")
            .baseUnit("things")
            .tags("tag", "value")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(Metrics.globalRegistry);

    // when
    summary.record(50);
    summary.record(100);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testSummaryPercentiles.percentile",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point -> assertThat(point).attributes().containsEntry("phi", "0.5"))
                        .anySatisfy(
                            point -> assertThat(point).attributes().containsEntry("phi", "0.95"))
                        .anySatisfy(
                            point -> assertThat(point).attributes().containsEntry("phi", "0.99"))));
  }
}
