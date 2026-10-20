/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;

import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LibraryInstrumentationExtensionTest {

  static {
    // TODO: remove once test dependency on javaagent-tooling is removed
    System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");
  }

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // repeated test verifies that the telemetry data is cleared between test runs
  @RepeatedTest(5)
  void shouldCollectTraces() {
    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testing.runWithSpan("child", () -> {});
          return null;
        });

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                parentSpan -> parentSpan.hasName("parent"),
                childSpan -> childSpan.hasName("child")));
  }

  @Test
  void shouldAssertAllMetricsForScope() {
    Attributes firstAttributes = Attributes.of(stringKey("key"), "first");
    Attributes secondAttributes = Attributes.of(stringKey("key"), "second");
    testing
        .getOpenTelemetry()
        .getMeter("test")
        .histogramBuilder("duration")
        .setUnit("s")
        .setDescription("Test duration.")
        .build()
        .record(2, firstAttributes);
    testing
        .getOpenTelemetry()
        .getMeter("test")
        .histogramBuilder("duration")
        .setUnit("s")
        .setDescription("Test duration.")
        .build()
        .record(4, secondAttributes);
    testing
        .getOpenTelemetry()
        .getMeter("test")
        .histogramBuilder("other")
        .setUnit("ms")
        .build()
        .record(3);
    testing
        .getOpenTelemetry()
        .getMeter("other-scope")
        .histogramBuilder("duration")
        .build()
        .record(5);

    testing.waitAndAssertMetricsExactly(
        "test",
        histogram(
            metric -> metric.hasName("duration").hasUnit("s").hasDescription("Test duration."),
            point -> point.hasCount(1).hasSum(4).hasAttributes(secondAttributes),
            point -> point.hasCount(1).hasSum(2).hasAttributes(firstAttributes)),
        histogram(
            metric -> metric.hasName("other").hasUnit("ms"), point -> point.hasCount(1).hasSum(3)));
  }
}
