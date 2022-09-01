/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import static io.opentelemetry.instrumentation.micrometer1shim.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractTimerSecondsTest {

  protected abstract InstrumentationExtension testing();

  @Test
  void testTimerWithBaseUnitSeconds() {
    // given
    Timer timer =
        Timer.builder("testTimerSeconds")
            .description("This is a test timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timer.record(1, TimeUnit.SECONDS);
    timer.record(10, TimeUnit.SECONDS);
    timer.record(12_345, TimeUnit.MILLISECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimerSeconds",
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
                                                .hasSum(23.345)
                                                .hasCount(3)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimerSeconds.max",
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
                                                .hasValue(12.345)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(timer);
    testing().clearData();
    timer.record(12, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testTimerSeconds", AbstractIterableAssert::isEmpty);
  }
}
