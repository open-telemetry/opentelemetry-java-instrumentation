/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GaugeTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testGauge() {
    // when
    Gauge gauge =
        Gauge.builder("testGauge", () -> 42)
            .description("This is a test gauge")
            .tags("tag", "value")
            .baseUnit("items")
            .register(Metrics.globalRegistry);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testGauge",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test gauge")
                        .hasUnit("items")
                        .hasDoubleGauge()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(42)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "value")))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(gauge);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testGauge", AbstractIterableAssert::isEmpty);
  }

  @Test
  void gaugesWithSameNameAndDifferentTags() {
    // when
    Gauge.builder("testGaugeWithTags", () -> 12)
        .description("First description wins")
        .baseUnit("items")
        .tags("tag", "1")
        .register(Metrics.globalRegistry);
    Gauge.builder("testGaugeWithTags", () -> 42)
        .description("ignored")
        .baseUnit("ignored")
        .tags("tag", "2")
        .register(Metrics.globalRegistry);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testGaugeWithTags",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("First description wins")
                        .hasUnit("items")
                        .hasDoubleGauge()
                        .points()
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(12)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "1")))
                        .anySatisfy(
                            point ->
                                assertThat(point)
                                    .hasValue(42)
                                    .attributes()
                                    .containsOnly(attributeEntry("tag", "2")))));
  }

  @Test
  void testWeakRefGauge() throws InterruptedException {
    // when
    AtomicLong num = new AtomicLong(42);
    Gauge.builder("testWeakRefGauge", num, AtomicLong::get)
        .strongReference(false)
        .register(Metrics.globalRegistry);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testWeakRefGauge",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGauge()
                        .points()
                        .satisfiesExactly(point -> assertThat(point).hasValue(42))));
    testing.clearData();

    // when
    WeakReference<AtomicLong> numWeakRef = new WeakReference<>(num);
    num = null;
    GcUtils.awaitGc(numWeakRef);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testWeakRefGauge", AbstractIterableAssert::isEmpty);
  }
}
