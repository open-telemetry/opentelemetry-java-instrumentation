/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import static io.opentelemetry.instrumentation.micrometer1shim.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
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
import org.junit.jupiter.api.Test;

public abstract class AbstractMeterTest {

  protected abstract InstrumentationExtension testing();

  @Test
  void testMeter() {
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
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(12345)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
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
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(12345)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
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
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(12345)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
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
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(12345)
                                                    .hasAttributes(
                                                        attributeEntry("tag", "value"))))));
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
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12345)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
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
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12345)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
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
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12345)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
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
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12345)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(meter);
    testing().clearData();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testMeter.total", AbstractIterableAssert::isEmpty);
  }
}
