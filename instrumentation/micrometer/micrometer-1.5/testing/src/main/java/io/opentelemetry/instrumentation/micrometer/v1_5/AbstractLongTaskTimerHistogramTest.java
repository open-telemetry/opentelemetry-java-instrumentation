/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MockClock;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public abstract class AbstractLongTaskTimerHistogramTest {

  protected abstract InstrumentationExtension testing();

  protected abstract MockClock clock();

  @Test
  void testMicrometerHistogram() {
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
    clock().add(Duration.ofMillis(100));
    LongTaskTimer.Sample sample2 = timer.start();
    LongTaskTimer.Sample sample3 = timer.start();
    clock().add(Duration.ofMillis(10));

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerHistogram.active",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("tasks")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(3)
                                                    .hasAttributes(Attributes.empty())))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerHistogram.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("ms")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasAttributes(Attributes.empty())
                                                    .satisfies(
                                                        pointData ->
                                                            assertThat(pointData.getValue())
                                                                .isPositive())))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerHistogram.histogram",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributes(attributeEntry("le", "100"))
                                                .hasValue(2),
                                        point ->
                                            point
                                                .hasAttributes(attributeEntry("le", "1000"))
                                                .hasValue(3)))));

    // when
    sample1.stop();
    sample2.stop();
    sample3.stop();

    // then
    // Continues to report 0 after stopped.
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerHistogram.active",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("tasks")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(0)
                                                    .hasAttributes(Attributes.empty())))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerHistogram.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("ms")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(0)
                                                    .hasAttributes(Attributes.empty())))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerHistogram.histogram",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributes(attributeEntry("le", "100")),
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributes(attributeEntry("le", "1000"))))));
  }
}
