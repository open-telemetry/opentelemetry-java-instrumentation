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
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;

public abstract class AbstractLongTaskTimerSecondsTest {

  protected abstract InstrumentationExtension testing();

  @Test
  void testLongTaskTimerWithBaseUnitSeconds() throws InterruptedException {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testLongTaskTimerSeconds")
            .description("This is a test long task timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample = timer.start();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerSeconds.active",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test long task timer")
                            .hasUnit("tasks")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(1)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerSeconds.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test long task timer")
                            .hasUnit("s")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasAttributes(attributeEntry("tag", "value"))
                                                    .satisfies(
                                                        pointData ->
                                                            assertThat(pointData.getValue())
                                                                .isPositive())))));

    // when
    TimeUnit.MILLISECONDS.sleep(100);
    sample.stop();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerSeconds.active",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerSeconds.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when timer is removed from the registry
    Metrics.globalRegistry.remove(timer);
    testing().clearData();
    timer.start();

    // then no tasks are active after starting a new sample
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimerSeconds.active",
            AbstractIterableAssert::isEmpty);
  }
}
