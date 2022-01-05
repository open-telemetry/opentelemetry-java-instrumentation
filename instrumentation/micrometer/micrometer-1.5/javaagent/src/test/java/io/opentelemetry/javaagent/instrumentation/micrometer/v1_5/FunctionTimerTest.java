/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class FunctionTimerTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testFunctionTimer() throws Exception {
    // given
    MyTimer timerObj = new MyTimer();

    FunctionTimer functionTimer =
        FunctionTimer.builder(
                "testFunctionTimer",
                timerObj,
                MyTimer::getCount,
                MyTimer::getTotalTimeNanos,
                TimeUnit.NANOSECONDS)
            .description("This is a test function timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timerObj.add(42, TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
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
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testFunctionTimer.total_time",
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
    Thread.sleep(10); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testFunctionTimer.count", AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testFunctionTimer.total_time", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testNanoPrecision() {
    // given
    MyTimer timerObj = new MyTimer();

    FunctionTimer.builder(
            "testNanoFunctionTimer",
            timerObj,
            MyTimer::getCount,
            MyTimer::getTotalTimeNanos,
            TimeUnit.NANOSECONDS)
        .register(Metrics.globalRegistry);

    // when
    timerObj.add(1_234_000, TimeUnit.NANOSECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testNanoFunctionTimer.total_time",
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
    MyTimer timerObj1 = new MyTimer();
    MyTimer timerObj2 = new MyTimer();

    FunctionTimer.builder(
            "testFunctionTimerWithTags",
            timerObj1,
            MyTimer::getCount,
            MyTimer::getTotalTimeNanos,
            TimeUnit.NANOSECONDS)
        .tags("tag", "1")
        .register(Metrics.globalRegistry);

    FunctionTimer.builder(
            "testFunctionTimerWithTags",
            timerObj2,
            MyTimer::getCount,
            MyTimer::getTotalTimeNanos,
            TimeUnit.NANOSECONDS)
        .tags("tag", "2")
        .register(Metrics.globalRegistry);

    // when
    timerObj1.add(12, TimeUnit.SECONDS);
    timerObj2.add(42, TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testFunctionTimerWithTags.total_time",
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

  static class MyTimer {
    int count = 0;
    long totalTimeNanos = 0;

    void add(long time, TimeUnit unit) {
      count++;
      totalTimeNanos += unit.toNanos(time);
    }

    public int getCount() {
      return count;
    }

    public double getTotalTimeNanos() {
      return totalTimeNanos;
    }
  }
}
