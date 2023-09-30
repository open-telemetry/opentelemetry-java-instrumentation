/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractFunctionTimerTest {

  protected abstract InstrumentationExtension testing();

  final TestTimer timerObj = new TestTimer();
  final TestTimer anotherTimerObj = new TestTimer();

  @BeforeEach
  void cleanupTimers() {
    timerObj.reset();
    anotherTimerObj.reset();
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testFunctionTimer() {
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
                            .hasUnit("{invocation}")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(1)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testFunctionTimer.sum",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test function timer")
                            .hasUnit("s")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(42)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(functionTimer);
    testing().clearData();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionTimer.count", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testFunctionTimerDependingOnThreadContextClassLoader() {
    // given
    ClassLoader dummy = new URLClassLoader(new URL[0]);
    ClassLoader prior = Thread.currentThread().getContextClassLoader();
    FunctionTimer functionTimer;
    try {
      Thread.currentThread().setContextClassLoader(dummy);
      functionTimer =
          FunctionTimer.builder(
                  "testFunctionTimer",
                  timerObj,
                  timerObj -> {
                    // will throw an exception before value is reported if assertion fails
                    // then we assert below that value was reported
                    assertThat(Thread.currentThread().getContextClassLoader()).isEqualTo(dummy);
                    return timerObj.getCount();
                  },
                  timerObj -> {
                    // will throw an exception before value is reported if assertion fails
                    // then we assert below that value was reported
                    assertThat(Thread.currentThread().getContextClassLoader()).isEqualTo(dummy);
                    return timerObj.getTotalTimeNanos();
                  },
                  TimeUnit.NANOSECONDS)
              .description("This is a test function timer")
              .tags("tag", "value")
              .register(Metrics.globalRegistry);
    } finally {
      Thread.currentThread().setContextClassLoader(prior);
    }

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
                            .hasUnit("{invocation}")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(1)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testFunctionTimer.sum",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test function timer")
                            .hasUnit("s")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(42)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(functionTimer);
    testing().clearData();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionTimer.count", AbstractIterableAssert::isEmpty);
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
                            .hasUnit("s")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(0.001234)
                                                .hasAttributes(Attributes.empty())))));
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
                            .hasUnit("s")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12)
                                                .hasAttributes(attributeEntry("tag", "1")),
                                        point ->
                                            point
                                                .hasValue(42)
                                                .hasAttributes(attributeEntry("tag", "2"))))));
  }
}
