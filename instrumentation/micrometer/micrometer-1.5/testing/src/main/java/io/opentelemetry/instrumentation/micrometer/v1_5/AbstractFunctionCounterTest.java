/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractFunctionCounterTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  protected abstract InstrumentationExtension testing();

  final AtomicLong num = new AtomicLong(12);
  final AtomicLong anotherNum = new AtomicLong(13);

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testFunctionCounter() throws InterruptedException {
    // when
    FunctionCounter counter =
        FunctionCounter.builder("testFunctionCounter", num, AtomicLong::get)
            .description("This is a test function counter")
            .tags("tag", "value")
            .baseUnit("items")
            .register(Metrics.globalRegistry);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testFunctionCounter",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test function counter")
                            .hasUnit("items")
                            .hasDoubleSum()
                            .isMonotonic()
                            .points()
                            .satisfiesExactly(
                                point ->
                                    assertThat(point)
                                        .hasValue(12)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "value")))));

    // when
    Metrics.globalRegistry.remove(counter);
    Thread.sleep(10); // give time for any inflight metric export to be received
    testing().clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionCounter", AbstractIterableAssert::isEmpty);
  }

  @Test
  void functionCountersWithSameNameAndDifferentTags() {
    // when
    FunctionCounter.builder("testFunctionCounterWithTags", num, AtomicLong::get)
        .description("First description wins")
        .tags("tag", "1")
        .baseUnit("items")
        .register(Metrics.globalRegistry);
    FunctionCounter.builder("testFunctionCounterWithTags", anotherNum, AtomicLong::get)
        .description("ignored")
        .tags("tag", "2")
        .baseUnit("items")
        .register(Metrics.globalRegistry);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testFunctionCounterWithTags",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("First description wins")
                            .hasUnit("items")
                            .hasDoubleSum()
                            .isMonotonic()
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
                                        .hasValue(13)
                                        .attributes()
                                        .containsOnly(attributeEntry("tag", "2")))));
  }
}
