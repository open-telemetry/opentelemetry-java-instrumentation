/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MockClock;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Deprecated
class LongTaskTimerHistogramTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static MockClock clock = new MockClock();
  static MeterRegistry otelMeterRegistry;

  @BeforeAll
  public static void setUpRegistry() {
    otelMeterRegistry =
        OpenTelemetryMeterRegistry.builder(testing.getOpenTelemetry()).setClock(clock).build();
    Metrics.addRegistry(otelMeterRegistry);
  }

  @AfterAll
  public static void tearDownRegistry() {
    Metrics.removeRegistry(otelMeterRegistry);
  }

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testMicrometerHistogram() {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testLongTaskTimerHistogram")
            .description("This is a test timer")
            .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(1000))
            .distributionStatisticBufferLength(10)
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample1 = timer.start();
    // only active tasks count
    timer.start().stop();
    clock.add(Duration.ofMillis(100));
    LongTaskTimer.Sample sample2 = timer.start();
    LongTaskTimer.Sample sample3 = timer.start();
    clock.add(Duration.ofMillis(10));

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimerHistogram.histogram",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(2)
                                    .attributes()
                                    .containsEntry("le", "100"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(3)
                                    .attributes()
                                    .containsEntry("le", "1000"))));

    // when
    sample1.stop();
    sample2.stop();
    sample3.stop();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testLongTaskTimerHistogram.histogram",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(0)
                                    .attributes()
                                    .containsEntry("le", "100"))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(0)
                                    .attributes()
                                    .containsEntry("le", "1000"))));
  }
}
