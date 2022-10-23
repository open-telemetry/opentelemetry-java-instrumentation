/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractTimerTest {

  protected abstract InstrumentationExtension testing();

  @Test
  void testTimer() {
    // given
    Timer timer =
        Timer.builder("testTimer")
            .description("This is a test timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timer.record(42, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(42_000)
                                                .hasCount(1)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(42_000)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(timer);
    testing().clearData();
    timer.record(12, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(INSTRUMENTATION_NAME, "testTimer", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testNanoPrecision() {
    // given
    Timer timer = Timer.builder("testNanoTimer").register(Metrics.globalRegistry);

    // when
    timer.record(1_234_000, TimeUnit.NANOSECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testNanoTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(1.234)
                                                .hasCount(1)
                                                .hasAttributes(Attributes.empty())))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testNanoTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1.234)
                                                .hasAttributes(Attributes.empty())))));
  }

  @Test
  void testMicrometerHistogram() {
    // given
    Timer timer =
        Timer.builder("testTimer")
            .description("This is a test timer")
            .tags("tag", "value")
            .serviceLevelObjectives(
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(100),
                Duration.ofSeconds(1000))
            .distributionStatisticBufferLength(10)
            .register(Metrics.globalRegistry);

    // when
    timer.record(500, TimeUnit.MILLISECONDS);
    timer.record(5, TimeUnit.SECONDS);
    timer.record(50, TimeUnit.SECONDS);
    timer.record(500, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(555500)
                                                .hasCount(4)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(500000)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer.histogram",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributes(
                                                    attributeEntry("le", "1000"),
                                                    attributeEntry("tag", "value")),
                                        point ->
                                            point
                                                .hasValue(2)
                                                .hasAttributes(
                                                    attributeEntry("le", "10000"),
                                                    attributeEntry("tag", "value")),
                                        point ->
                                            point
                                                .hasValue(3)
                                                .hasAttributes(
                                                    attributeEntry("le", "100000"),
                                                    attributeEntry("tag", "value")),
                                        point ->
                                            point
                                                .hasValue(4)
                                                .hasAttributes(
                                                    attributeEntry("le", "1000000"),
                                                    attributeEntry("tag", "value"))))));
  }

  @Test
  void testMicrometerPercentiles() {
    // given
    Timer timer =
        Timer.builder("testTimer")
            .description("This is a test timer")
            .tags("tag", "value")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(Metrics.globalRegistry);

    // when
    timer.record(50, TimeUnit.MILLISECONDS);
    timer.record(100, TimeUnit.MILLISECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(150)
                                                .hasCount(2)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(100)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer.percentile",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("phi", "0.5"),
                                                attributeEntry("tag", "value")),
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("phi", "0.95"),
                                                attributeEntry("tag", "value")),
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("phi", "0.99"),
                                                attributeEntry("tag", "value"))))));
  }
}
