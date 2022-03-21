/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractFunctionTimerTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  protected abstract InstrumentationExtension testing();

  final TestTimer timerObj = new TestTimer();
  final TestTimer anotherTimerObj = new TestTimer();

  @BeforeEach
  void cleanupMeters() {
    timerObj.reset();
    anotherTimerObj.reset();
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testFunctionTimer() throws InterruptedException {
    // given
    FunctionTimer functionTimer =
        FunctionTimer.builder(
                "testFunctionTimer",
                timerObj,
                TestTimer::getCount,
                TestTimer::getTotalTimeNanos,
                TimeUnit.NANOSECONDS)
            .description("This is a test function timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timerObj.add(42, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testFunctionTimer.count",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test function timer")
                            .hasUnit("1")
                            .hasLongSum()
                            .isMonotonic()
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
            "testFunctionTimer.sum",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test function timer")
                            .hasUnit("ms")
                            .hasDoubleSum()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(42_000)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));

    // when
    Metrics.globalRegistry.remove(functionTimer);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing().clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionTimer.count", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionTimer.sum", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testNanoPrecision() {
    // given
    FunctionTimer.builder(
            "testNanoFunctionTimer",
            timerObj,
            TestTimer::getCount,
            TestTimer::getTotalTimeNanos,
            TimeUnit.NANOSECONDS)
        .register(Metrics.globalRegistry);

    // when
    timerObj.add(1_234_000, TimeUnit.NANOSECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testNanoFunctionTimer.sum",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasDoubleSum()
                            .points()
                            .satisfiesExactly(
                                point -> assertThat(point).hasValue(1.234).attributes())));
  }

  @Test
  void functionTimersWithSameNameAndDifferentTags() {
    // given
    FunctionTimer.builder(
            "testFunctionTimerWithTags",
            timerObj,
            TestTimer::getCount,
            TestTimer::getTotalTimeNanos,
            TimeUnit.NANOSECONDS)
        .tags("tag", "1")
        .register(Metrics.globalRegistry);

    FunctionTimer.builder(
            "testFunctionTimerWithTags",
            anotherTimerObj,
            TestTimer::getCount,
            TestTimer::getTotalTimeNanos,
            TimeUnit.NANOSECONDS)
        .tags("tag", "2")
        .register(Metrics.globalRegistry);

    // when
    timerObj.add(12, TimeUnit.SECONDS);
    anotherTimerObj.add(42, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testFunctionTimerWithTags.sum",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasDoubleSum()
                            .points()
                            .anySatisfy(
                                point ->
                                    assertThat(point)
                                        .hasValue(12_000)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "1")))
                            .anySatisfy(
                                point ->
                                    assertThat(point)
                                        .hasValue(42_000)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "2")))));
  }
}
