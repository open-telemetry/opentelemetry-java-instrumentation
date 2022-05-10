/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("PreferJavaTimeOverload")
class TimerTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testTimer() throws Exception {
    // given
    Timer timer =
        Timer.builder("testTimer")
            .description("This is a test timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timer.record(42, TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
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
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(timer);
    timer.record(12, TimeUnit.SECONDS);

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testTimerMax", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testNanoPrecision() {
    // given
    Timer timer = Timer.builder("testNanoTimer").register(Metrics.globalRegistry);

    // when
    timer.record(1_234_000, TimeUnit.NANOSECONDS);

    // then
    testing.waitAndAssertMetrics(
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
                                    point -> point.hasSum(1.234).hasCount(1)))));
  }

  @Test
  void testMicrometerHistogram() {
    // given
    Timer timer =
        Timer.builder("testTimerHistogram")
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
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testTimerHistogram.histogram",
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
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "1000")),
                                    point ->
                                        point
                                            .hasValue(2)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "10000")),
                                    point ->
                                        point
                                            .hasValue(3)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(AttributeKey.stringKey("le"), "100000")),
                                    point ->
                                        point
                                            .hasValue(4)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "value"),
                                                equalTo(
                                                    AttributeKey.stringKey("le"), "1000000"))))));
  }

  @Test
  void testMicrometerPercentiles() {
    // given
    Timer timer =
        Timer.builder("testTimerPercentiles")
            .description("This is a test timer")
            .tags("tag", "value")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(Metrics.globalRegistry);

    // when
    timer.record(50, TimeUnit.MILLISECONDS);
    timer.record(100, TimeUnit.MILLISECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testTimerPercentiles.percentile",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AttributeKey.stringKey("tag"), "value"),
                                            equalTo(AttributeKey.stringKey("phi"), "0.5")),
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AttributeKey.stringKey("tag"), "value"),
                                            equalTo(AttributeKey.stringKey("phi"), "0.95")),
                                    point ->
                                        point.hasAttributesSatisfying(
                                            equalTo(AttributeKey.stringKey("tag"), "value"),
                                            equalTo(AttributeKey.stringKey("phi"), "0.99"))))));
  }

  @Test
  void testMicrometerMax() throws InterruptedException {
    // given
    Timer timer =
        Timer.builder("testTimerMax")
            .description("This is a test timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timer.record(1, TimeUnit.SECONDS);
    timer.record(2, TimeUnit.SECONDS);
    timer.record(4, TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testTimerMax.max",
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
                                            .hasValue(4_000)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));

    // when
    Metrics.globalRegistry.remove(timer);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testTimerMax.max", AbstractIterableAssert::isEmpty);
  }
}
