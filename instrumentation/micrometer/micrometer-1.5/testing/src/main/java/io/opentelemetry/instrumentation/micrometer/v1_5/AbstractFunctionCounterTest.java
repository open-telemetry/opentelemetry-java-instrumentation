/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractFunctionCounterTest {

  protected abstract InstrumentationExtension testing();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  final AtomicLong num = new AtomicLong(12);
  final AtomicLong anotherNum = new AtomicLong(13);

  @Test
  void testFunctionCounter() {
    // given
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
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(counter);
    testing().clearData();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionCounter", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testFunctionCounterDependingOnThreadContextClassLoader() {
    // given
    ClassLoader dummy = new URLClassLoader(new URL[0]);
    ClassLoader prior = Thread.currentThread().getContextClassLoader();
    FunctionCounter counter;
    try {
      Thread.currentThread().setContextClassLoader(dummy);
      counter =
          FunctionCounter.builder(
                  "testFunctionCounter",
                  num,
                  num -> {
                    // will throw an exception before value is reported if assertion fails
                    // then we assert below that value was reported
                    assertThat(Thread.currentThread().getContextClassLoader()).isEqualTo(dummy);
                    return num.get();
                  })
              .description("This is a test function counter")
              .tags("tag", "value")
              .baseUnit("items")
              .register(Metrics.globalRegistry);
    } finally {
      Thread.currentThread().setContextClassLoader(prior);
    }

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
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // when
    Metrics.globalRegistry.remove(counter);
    testing().clearData();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testFunctionCounter", AbstractIterableAssert::isEmpty);
  }

  @Test
  void functionCountersWithSameNameAndDifferentTags() {
    // given
    FunctionCounter.builder("testFunctionCounterWithTags", num, AtomicLong::get)
        .description("First description")
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
                            .hasDescription("First description")
                            .hasUnit("items")
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.isMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasValue(12)
                                                    .hasAttributes(attributeEntry("tag", "1")),
                                            point ->
                                                point
                                                    .hasValue(13)
                                                    .hasAttributes(attributeEntry("tag", "2"))))));
  }
}
