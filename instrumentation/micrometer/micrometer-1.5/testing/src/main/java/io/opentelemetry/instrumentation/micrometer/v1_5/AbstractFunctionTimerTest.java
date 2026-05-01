/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.URL;
import java.net.URLClassLoader;
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
                NANOSECONDS)
            .description("This is a test function timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timerObj.add(42, SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testFunctionTimer.count")
                    .hasDescription("This is a test function timer")
                    .hasUnit("{invocation}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.isMonotonic()
                                .hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("tag"), "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testFunctionTimer.sum")
                    .hasDescription("This is a test function timer")
                    .hasUnit("s")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(42)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "value")))));

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
                  NANOSECONDS)
              .description("This is a test function timer")
              .tags("tag", "value")
              .register(Metrics.globalRegistry);
    } finally {
      Thread.currentThread().setContextClassLoader(prior);
    }

    // when
    timerObj.add(42, SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testFunctionTimer.count")
                    .hasDescription("This is a test function timer")
                    .hasUnit("{invocation}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.isMonotonic()
                                .hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("tag"), "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testFunctionTimer.sum")
                    .hasDescription("This is a test function timer")
                    .hasUnit("s")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(42)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "value")))));

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
            NANOSECONDS)
        .register(Metrics.globalRegistry);

    // when
    timerObj.add(1_234_000, NANOSECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testNanoFunctionTimer.sum")
                    .hasUnit("s")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point.hasValue(0.001234).hasAttributesSatisfyingExactly())));
  }

  @Test
  void functionTimersWithSameNameAndDifferentTags() {
    // given
    FunctionTimer.builder(
            "testFunctionTimerWithTags",
            timerObj,
            TestTimer::getCount,
            TestTimer::getTotalTimeNanos,
            NANOSECONDS)
        .tags("tag", "1")
        .register(Metrics.globalRegistry);
    FunctionTimer.builder(
            "testFunctionTimerWithTags",
            anotherTimerObj,
            TestTimer::getCount,
            TestTimer::getTotalTimeNanos,
            NANOSECONDS)
        .tags("tag", "2")
        .register(Metrics.globalRegistry);

    // when
    timerObj.add(12, SECONDS);
    anotherTimerObj.add(42, SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testFunctionTimerWithTags.sum")
                    .hasUnit("s")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(12)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "1")),
                                point ->
                                    point
                                        .hasValue(42)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "2")))));
  }
}
