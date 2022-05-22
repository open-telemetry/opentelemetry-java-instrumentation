/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LongTaskTimerSecondsTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

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
    testing.waitAndAssertMetrics(
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
                                                .hasAttributesSatisfying(
                                                    equalTo(
                                                        AttributeKey.stringKey("tag"),
                                                        "value"))))));
    testing.waitAndAssertMetrics(
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
                                        point -> {
                                          point.hasAttributesSatisfying(
                                              equalTo(AttributeKey.stringKey("tag"), "value"));
                                          // any value >0 - duration of currently running tasks
                                          assertThat(metric.getDoubleSumData().getPoints())
                                              .satisfiesExactly(
                                                  p -> assertThat(p.getValue()).isPositive());
                                        }))));

    // when
    TimeUnit.MILLISECONDS.sleep(100);
    sample.stop();

    // then
    testing.waitAndAssertMetrics(
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
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
    testing.waitAndAssertMetrics(
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
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));

    // when timer is removed from the registry
    Metrics.globalRegistry.remove(timer);
    Thread.sleep(100); // give time for any in flight metric export to be received
    testing.clearData();
    timer.start();

    // then no tasks are active after starting a new sample
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testLongTaskTimerSeconds.active", AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testLongTaskTimerSeconds.duration", AbstractIterableAssert::isEmpty);
  }
}
