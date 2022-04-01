/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupportabilityMetricsTest {
  @Test
  void disabled() {
    List<String> reports = new ArrayList<>();
    SupportabilityMetrics metrics =
        new SupportabilityMetrics(configWithJavaagentDebug(false), reports::add);

    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.SERVER, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.INTERNAL, "otherInstrumentation");
    metrics.incrementCounter("some counter");
    metrics.incrementCounter("another counter");
    metrics.incrementCounter("some counter");

    metrics.report();

    assertThat(reports).isEmpty();
  }

  @Test
  void reportsMetrics() {
    List<String> reports = new ArrayList<>();
    SupportabilityMetrics metrics =
        new SupportabilityMetrics(configWithJavaagentDebug(true), reports::add);

    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.SERVER, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.INTERNAL, "otherInstrumentation");
    metrics.incrementCounter("some counter");
    metrics.incrementCounter("another counter");
    metrics.incrementCounter("some counter");

    metrics.report();

    assertThat(reports)
        .containsExactlyInAnyOrder(
            "Suppressed Spans by 'favoriteInstrumentation' (CLIENT) : 2",
            "Suppressed Spans by 'favoriteInstrumentation' (SERVER) : 1",
            "Suppressed Spans by 'otherInstrumentation' (INTERNAL) : 1",
            "Counter 'some counter' : 2",
            "Counter 'another counter' : 1");
  }

  @Test
  void resetsCountsEachReport() {
    List<String> reports = new ArrayList<>();
    SupportabilityMetrics metrics =
        new SupportabilityMetrics(configWithJavaagentDebug(true), reports::add);

    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.incrementCounter("some counter");

    metrics.report();
    metrics.report();

    assertThat(reports)
        .containsExactlyInAnyOrder(
            "Suppressed Spans by 'favoriteInstrumentation' (CLIENT) : 1",
            "Counter 'some counter' : 1");
  }

  private static Config configWithJavaagentDebug(boolean enabled) {
    return Config.builder()
        .addProperty("otel.javaagent.debug", Boolean.toString(enabled))
        .build();
  }
}
