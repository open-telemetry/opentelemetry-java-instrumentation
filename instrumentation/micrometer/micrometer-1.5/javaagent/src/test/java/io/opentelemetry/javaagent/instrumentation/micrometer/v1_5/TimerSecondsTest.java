/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("PreferJavaTimeOverload")
class TimerSecondsTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometershim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testTimerWithBaseUnitSeconds() throws InterruptedException {
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
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testTimerSeconds",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test timer")
                        .hasUnit("s")
                        .hasDoubleHistogram()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasSum(23.345)
                                    .hasCount(3)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testTimerSeconds.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test timer")
                        .hasUnit("s")
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(12.345)
                                    .attributes()
                                    .containsEntry("tag", "value"))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(timer);
    timer.record(12, TimeUnit.SECONDS);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testTimerSeconds",
        metrics ->
            metrics.allSatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleHistogram()
                        .points()
                        .noneSatisfy(point -> assertThat(point).hasSum(35.345).hasCount(4))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testTimerMax.max", AbstractIterableAssert::isEmpty);
  }
}
