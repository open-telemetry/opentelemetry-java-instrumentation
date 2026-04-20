/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractTimerHistogramGaugesTest {

  protected abstract InstrumentationExtension testing();

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
    timer.record(500, MILLISECONDS);
    timer.record(5, SECONDS);
    timer.record(50, SECONDS);
    timer.record(500, SECONDS);

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
                                                .hasSum(555.5)
                                                .hasCount(4)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasValue(500)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "1"),
                                                    equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point
                                                .hasValue(2)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "10"),
                                                    equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point
                                                .hasValue(3)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "100"),
                                                    equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point
                                                .hasValue(4)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("le"), "1000"),
                                                    equalTo(stringKey("tag"), "value"))))));
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
    timer.record(50, MILLISECONDS);
    timer.record(500, MILLISECONDS);

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
                                                .hasSum(0.55)
                                                .hasCount(2)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                                .hasValue(0.5)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("tag"), "value"))))));
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
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("phi"), "0.5"),
                                                equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("phi"), "0.95"),
                                                equalTo(stringKey("tag"), "value")),
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("phi"), "0.99"),
                                                equalTo(stringKey("tag"), "value"))))));
  }
}
