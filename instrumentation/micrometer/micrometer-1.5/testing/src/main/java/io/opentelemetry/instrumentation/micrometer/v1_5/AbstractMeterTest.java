/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Statistic;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractMeterTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testMeter() throws InterruptedException {
    // given
    AtomicReference<Double> number = new AtomicReference<>(12345.0);

    List<Measurement> measurements =
        Arrays.asList(
            new Measurement(number::get, Statistic.TOTAL),
            new Measurement(number::get, Statistic.TOTAL_TIME),
            new Measurement(number::get, Statistic.COUNT),
            new Measurement(number::get, Statistic.ACTIVE_TASKS),
            new Measurement(number::get, Statistic.DURATION),
            new Measurement(number::get, Statistic.MAX),
            new Measurement(number::get, Statistic.VALUE),
            new Measurement(number::get, Statistic.UNKNOWN));

    // when
    Meter meter =
        Meter.builder("testMeter", Meter.Type.OTHER, measurements)
            .description("This is a test meter")
            .baseUnit("things")
            .tag("tag", "value")
            .register(Metrics.globalRegistry);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.total",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleSum()
                            .isMonotonic()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.total_time",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleSum()
                            .isMonotonic()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.count",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleSum()
                            .isMonotonic()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.active",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleSum()
                            .isNotMonotonic()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleGauge()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleGauge()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.value",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleGauge()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testMeter.unknown",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test meter")
                            .hasUnit("things")
                            .hasDoubleGauge()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12345)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));

    // when
    Metrics.globalRegistry.remove(meter);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing().clearData();

    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.total", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.total_time", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.count", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.active", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.duration", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.max", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.value", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.unknown", AbstractIterableAssert::isEmpty);
  }
}
