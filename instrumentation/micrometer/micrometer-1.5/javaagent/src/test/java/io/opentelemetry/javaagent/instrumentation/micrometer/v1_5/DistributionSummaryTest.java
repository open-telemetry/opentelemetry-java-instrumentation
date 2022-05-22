/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DistributionSummaryTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testDistributionSummary() throws Exception {
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
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(42)
                                            .hasCount(1)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(summary);
    summary.record(6);

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testSummary", AbstractIterableAssert::isEmpty);
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
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "1")),
                                    point ->
                                        point
                                            .hasValue(2)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "10")),
                                    point ->
                                        point
                                            .hasValue(3)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "100")),
                                    point ->
                                        point
                                            .hasValue(4)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "1000"))))));
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
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AttributeKey.stringKey("tag"), "value"),
                                            equalTo(AttributeKey.stringKey("phi"), "0.5")),
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AttributeKey.stringKey("tag"), "value"),
                                            equalTo(AttributeKey.stringKey("phi"), "0.95")),
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AttributeKey.stringKey("tag"), "value"),
                                            equalTo(AttributeKey.stringKey("phi"), "0.99"))))));
  }

  @Test
  void testMicrometerMax() throws InterruptedException {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testSummaryMax")
            .description("This is a test distribution summary")
            .baseUnit("things")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    summary.record(1);
    summary.record(2);
    summary.record(4);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testSummaryMax.max",
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
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));

    // when
    Metrics.globalRegistry.remove(summary);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testSummaryMax.max", AbstractIterableAssert::isEmpty);
  }
}
