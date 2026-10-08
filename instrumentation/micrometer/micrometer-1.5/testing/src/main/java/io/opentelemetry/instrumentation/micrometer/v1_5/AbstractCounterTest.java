/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class AbstractCounterTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testCounter() {
    // given
    Counter counter =
        Counter.builder("testCounter")
            .description("This is a test counter")
            .tags("tag", "value")
            .baseUnit("items")
            .register(Metrics.globalRegistry);

    // when
    counter.increment();
    counter.increment(2);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testCounter")
                    .hasDescription("This is a test counter")
                    .hasUnit("items")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.isMonotonic()
                                .hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(3)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("tag"), "value")))));

    // when
    Metrics.globalRegistry.remove(counter);
    testing().clearData();
    counter.increment();

    // then
    testing()
        .waitAndAssertMetrics(INSTRUMENTATION_NAME, "testCounter", AbstractIterableAssert::isEmpty);
  }

  @ParameterizedTest
  @CsvSource({
    // known Micrometer units are normalized to UCUM under the v3 preview
    "bytes, By",
    "threads, {thread}",
    // percent has an ambiguous scale and is passed through unchanged
    "percent, percent",
    // unknown units are passed through unchanged
    "widgets, widgets",
    // already valid UCUM
    "ms, ms",
  })
  void testCounterBaseUnit(String baseUnit, String v3PreviewUnit) {
    Counter counter =
        Counter.builder("testCounterBaseUnit").baseUnit(baseUnit).register(Metrics.globalRegistry);

    counter.increment();

    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testCounterBaseUnit")
                    .hasUnit(SemconvStability.v3Preview() ? v3PreviewUnit : baseUnit));
  }

  @Test
  void testCounterWithoutBaseUnit() {
    Counter counter =
        Counter.builder("testCounterWithoutBaseUnit").register(Metrics.globalRegistry);

    counter.increment();

    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric -> metric.hasName("testCounterWithoutBaseUnit").hasUnit(""));
  }
}
