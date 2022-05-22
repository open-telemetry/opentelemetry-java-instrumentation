/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("PreferJavaTimeOverload")
class PrometheusModeTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  final TestTimer timerObj = new TestTimer();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testCounter() {
    // given
    Counter counter =
        Counter.builder("testPrometheusCounter")
            .description("This is a test counter")
            .tags("tag", "value")
            .baseUnit("items")
            .register(Metrics.globalRegistry);

    // when
    counter.increment(12);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusCounter.items",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test counter")
                        .hasUnit("items")
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12)
                                                .hasAttributesSatisfying(
                                                    equalTo(
                                                        AttributeKey.stringKey("tag"),
                                                        "value"))))));
  }

  @Test
  void testDistributionSummary() {
    // given
    DistributionSummary summary =
        DistributionSummary.builder("testPrometheusSummary")
            .description("This is a test summary")
            .baseUnit("items")
            .tag("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    summary.record(12);
    summary.record(42);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusSummary.items",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test summary")
                        .hasUnit("items")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(54)
                                            .hasCount(2)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusSummary.items.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test summary")
                        .hasUnit("items")
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(42)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
  }

  @Test
  void testFunctionTimer() {
    // given
    FunctionTimer.builder(
            "testPrometheusFunctionTimer",
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
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusFunctionTimer.seconds.count",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test function timer")
                        .hasUnit("1")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isMonotonic()
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
        "testPrometheusFunctionTimer.seconds.sum",
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
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
  }

  @Test
  void testGauge() {
    // when
    Gauge.builder("testPrometheusGauge", () -> 42)
        .description("This is a test gauge")
        .tags("tag", "value")
        .baseUnit("items")
        .register(Metrics.globalRegistry);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusGauge.items",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test gauge")
                        .hasUnit("items")
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(42)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
  }

  @Test
  void testLongTaskTimer() throws InterruptedException {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testPrometheusLongTaskTimer")
            .description("This is a test long task timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample = timer.start();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusLongTaskTimer.seconds.active",
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
        "testPrometheusLongTaskTimer.seconds.duration",
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
        "testPrometheusLongTaskTimer.seconds.active",
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
        "testPrometheusLongTaskTimer.seconds.duration",
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
  }

  @Test
  void testTimer() {
    // given
    Timer timer =
        Timer.builder("testPrometheusTimer")
            .description("This is a test timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timer.record(1, TimeUnit.SECONDS);
    timer.record(5, TimeUnit.SECONDS);
    timer.record(10_789, TimeUnit.MILLISECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusTimer.seconds",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test timer")
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(16.789)
                                            .hasCount(3)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testPrometheusTimer.seconds.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test timer")
                        .hasUnit("s")
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(10.789)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
  }
}
