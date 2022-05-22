/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CounterTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void cleanupMeters() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testCounter() throws Exception {
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
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testCounter",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test counter")
                        .hasUnit("items")
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(3)
                                                .hasAttributesSatisfying(
                                                    equalTo(
                                                        AttributeKey.stringKey("tag"),
                                                        "value"))))));
    testing.clearData();

    // when
    Metrics.globalRegistry.remove(counter);
    counter.increment();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testCounter", AbstractIterableAssert::isEmpty);
  }
}
