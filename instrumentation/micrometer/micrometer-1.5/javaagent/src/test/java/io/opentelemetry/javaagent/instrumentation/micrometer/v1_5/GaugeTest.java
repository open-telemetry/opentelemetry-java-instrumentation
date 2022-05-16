/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.common.AttributeKey;
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

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testGauge() throws InterruptedException {
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
                        .hasDoubleGaugeSatisfying(
                            g ->
                                g.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(42)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));

    // when
    Metrics.globalRegistry.remove(gauge);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testGauge", AbstractIterableAssert::isEmpty);
  }

  @Test
  void gaugesWithSameNameAndDifferentTags() {
    // when
    Gauge.builder("testGaugeWithTags", () -> 12)
        .description("This is a test gauge")
        .baseUnit("items")
        .tags("tag", "1")
        .register(Metrics.globalRegistry);
    Gauge.builder("testGaugeWithTags", () -> 42)
        .description("This is a test gauge")
        .baseUnit("items")
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
                        .hasDescription("This is a test gauge")
                        .hasUnit("items")
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(12)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "1")),
                                    point ->
                                        point
                                            .hasValue(42)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("tag"), "2"))))));
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
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(42)))));

    // when
    WeakReference<AtomicLong> numWeakRef = new WeakReference<>(num);
    num = null;
    GcUtils.awaitGc(numWeakRef);

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.clearData();
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testWeakRefGauge", AbstractIterableAssert::isEmpty);
  }
}
