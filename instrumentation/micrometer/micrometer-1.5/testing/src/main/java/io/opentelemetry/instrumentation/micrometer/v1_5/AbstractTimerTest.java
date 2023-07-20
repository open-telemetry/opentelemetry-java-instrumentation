/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractTimerTest {

  static final double[] NO_BUCKETS = new double[0];

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
                            .hasUnit("s")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(42)
                                                .hasCount(1)
                                                .hasAttributes(attributeEntry("tag", "value"))
                                                .hasBucketBoundaries(NO_BUCKETS)))));
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
                                                .hasValue(42)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // micrometer gauge histogram is not emitted
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testTimer.histogram", AbstractIterableAssert::isEmpty);

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
                            .hasUnit("s")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(0.001234)
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
                                                .hasValue(0.001234)
                                                .hasAttributes(Attributes.empty())))));
  }

  @Test
  void testMicrometerTimerWithCustomBuckets() {
    // given
    Timer timer =
        Timer.builder("testTimerWithCustomBuckets")
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
            "testTimerWithCustomBuckets",
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
                                                .hasSum(555.5)
                                                .hasCount(4)
                                                .hasAttributes(attributeEntry("tag", "value"))
                                                .satisfies(hasBucketBoundaries(1, 10, 100, 1_000))
                                                .hasBucketCounts(1, 1, 1, 1, 0)))));
  }

  private static ThrowingConsumer<HistogramPointData> hasBucketBoundaries(double... buckets) {
    return pointData ->
        assertThat(pointData.getBoundaries().stream().mapToDouble(d -> d).toArray())
            .contains(buckets, within(0.00001));
  }
}
