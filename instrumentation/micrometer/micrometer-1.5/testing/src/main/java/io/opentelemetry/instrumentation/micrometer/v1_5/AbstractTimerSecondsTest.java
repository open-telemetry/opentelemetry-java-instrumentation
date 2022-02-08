/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractTimerSecondsTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

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
                            .hasDoubleHistogram()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasSum(23.345)
                                        .hasCount(3)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing().clearData();

    // when
    Metrics.globalRegistry.remove(timer);
    timer.record(12, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimerSeconds",
            metrics ->
                metrics.allSatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleHistogram()
                            .points()
                            .noneSatisfy(point -> assertThat(point).hasSum(35.345).hasCount(4))));
  }
}
