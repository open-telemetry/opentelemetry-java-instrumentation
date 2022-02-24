/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("PreferJavaTimeOverload")
class NamingConventionTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static MeterRegistry otelMeterRegistry;

  @BeforeAll
  public static void setUpRegistry() {
    otelMeterRegistry = OpenTelemetryMeterRegistry.create(testing.getOpenTelemetry());
    otelMeterRegistry
        .config()
        .namingConvention(
            new NamingConvention() {
              @Override
              public String name(String name, Meter.Type type, String baseUnit) {
                return "test." + name;
              }

              @Override
              public String tagKey(String key) {
                return "test." + key;
              }

              @Override
              public String tagValue(String value) {
                return "test." + value;
              }
            });
    Metrics.addRegistry(otelMeterRegistry);
  }

  @AfterAll
  public static void tearDownRegistry() {
    Metrics.removeRegistry(otelMeterRegistry);
  }

  final AtomicLong num = new AtomicLong(42);

  @Test
  void renameCounter() {
    // when
    Counter counter = Metrics.counter("renamedCounter", "tag", "value");
    counter.increment();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedCounter",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }

  @Test
  void renameDistributionSummary() {
    // when
    DistributionSummary summary = Metrics.summary("renamedSummary", "tag", "value");
    summary.record(42);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedSummary",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleHistogram()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedSummary.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }

  @Test
  void renameFunctionCounter() {
    // when
    Metrics.more().counter("renamedFunctionCounter", Tags.of("tag", "value"), num);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedFunctionCounter",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }

  @Test
  void renameFunctionTimer() {
    // when
    Metrics.more()
        .timer(
            "renamedFunctionTimer",
            Tags.of("tag", "value"),
            num,
            AtomicLong::longValue,
            AtomicLong::doubleValue,
            TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedFunctionTimer.count",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedFunctionTimer.sum",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }

  @Test
  void renameGauge() {
    // when
    Metrics.gauge("renamedGauge", Tags.of("tag", "value"), num);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedGauge",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }

  @Test
  void renameLongTaskTimer() {
    // when
    LongTaskTimer timer = Metrics.more().longTaskTimer("renamedLongTaskTimer", "tag", "value");
    timer.start().stop();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedLongTaskTimer.active",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedLongTaskTimer.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleSum()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }

  @Test
  void renameTimer() {
    // when
    Timer timer = Metrics.timer("renamedTimer", "tag", "value");
    timer.record(10, TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedTimer",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleHistogram()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.renamedTimer.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .attributes()
                                    .containsOnly(attributeEntry("test.tag", "test.value")))));
  }
}
