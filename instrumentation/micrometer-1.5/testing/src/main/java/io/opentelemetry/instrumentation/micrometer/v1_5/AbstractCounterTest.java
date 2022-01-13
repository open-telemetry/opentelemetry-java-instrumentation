/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
            "testCounter",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test counter")
                            .hasUnit("items")
                            .hasDoubleSum()
                            .isMonotonic()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(3)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));
    testing().clearData();

    // when
    Metrics.globalRegistry.remove(counter);
    counter.increment();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testCounter",
            metrics ->
                metrics.allSatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSum()
                            .points()
                            .noneSatisfy(point -> assertThat(point).hasValue(4))));
  }
}
