/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractLongTaskTimerTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testLongTaskTimer() throws InterruptedException {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testLongTaskTimer")
            .description("This is a test long task timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample = timer.start();

    // then
    testing()
        .waitAndAssertMetrics(
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
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimer.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test long task timer")
                            .hasUnit("ms")
                            .hasDoubleSum()
                            .isNotMonotonic()
                            .points()
                            .satisfiesExactly(
                                point -> {
                                  assertThat(point)
                                      .attributes()
                                      .containsOnly(attributeEntry("tag", "value"));
                                  // any value >0 - duration of currently running tasks
                                  assertThat(point.getValue()).isPositive();
                                })));

    // when
    TimeUnit.MILLISECONDS.sleep(100);
    sample.stop();

    // then
    testing()
        .waitAndAssertMetrics(
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
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testLongTaskTimer.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSum()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(0)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));

    // when timer is removed from the registry
    Metrics.globalRegistry.remove(timer);
    Thread.sleep(10); // give time for any in flight metric export to be received
    testing().clearData();
    timer.start();

    // then no tasks are active after starting a new sample
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testLongTaskTimer.active", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testLongTaskTimer.duration", AbstractIterableAssert::isEmpty);
  }
}
