/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LongTaskTimerTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testLongTaskTimer() throws Exception {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testLongTaskTimer")
            .description("This is a test long task timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample = timer.start();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimer.active",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test long task timer")
                        .hasUnit("tasks")
                        .hasLongSum()
                        .isNotMonotonic()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(1)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));

    // when
    TimeUnit.MILLISECONDS.sleep(100);
    sample.stop();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimer",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test long task timer")
                        .hasUnit("ms")
                        .hasDoubleHistogram()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    /* .hasSumGreaterThan(100) */
                                    .hasCount(1)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimer.active",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(0)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(timer);
    timer.start();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimer.active",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(0)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));

    // when
    TimeUnit.MILLISECONDS.sleep(100);
    sample.stop();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimer",
        metrics ->
            metrics.allSatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleHistogram()
                        .points()
                        .noneSatisfy(
                            point ->
                                assertThat(point)
                                    /* .hasSumGreaterThan(200) */
                                    .hasCount(2))));
  }

  @Test
  void testMicrometerHistogram() throws InterruptedException {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testLongTaskTimerHistogram")
            .description("This is a test timer")
            .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(1000))
            .distributionStatisticBufferLength(10)
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample1 = timer.start();
    // only active tasks count
    timer.start().stop();
    TimeUnit.MILLISECONDS.sleep(100);
    LongTaskTimer.Sample sample2 = timer.start();
    LongTaskTimer.Sample sample3 = timer.start();
    TimeUnit.MILLISECONDS.sleep(10);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimerHistogram.histogram",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(2)
                                    .attributes()
                                    .containsEntry("le", "100"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(3)
                                    .attributes()
                                    .containsEntry("le", "1000"))));

    // when
    sample1.stop();
    sample2.stop();
    sample3.stop();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimerHistogram.histogram",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(0)
                                    .attributes()
                                    .containsEntry("le", "100"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(0)
                                    .attributes()
                                    .containsEntry("le", "1000"))));
  }
}
