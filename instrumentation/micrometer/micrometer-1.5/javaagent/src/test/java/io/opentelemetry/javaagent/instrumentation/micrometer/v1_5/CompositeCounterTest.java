/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CompositeCounterTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @AfterEach
  @BeforeEach
  public void cleanup() {
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
    assertThat(counter.getClass().getName()).contains("CompositeCounter");

    // when
    counter.increment();

    // then
    // OpenTelemetryCounter returns NaN for count(), but NoopCounter returns 0
    // Here we verify that OpenTelemetryCounter is filtered out when count() is called and the
    // result is produced form NoopCounter. If there were multiple meter registries, the result
    // would be produced from an instrument that is not from our registry. We don't test with
    // multiple registries because the behavior of count() depends on the order of elements in a
    // map, so instead we test that our instrument is ignored and the result comes from a fallback
    // to NoopCounter.
    assertThat(counter.count()).isEqualTo(0);

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
                                                .hasValue(1)
                                                .hasAttributes(attributeEntry("tag", "value"))))));
  }
}
