/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupportabilityMetricsTest {
  @Test
  void disabled() {
    List<String> reports = new ArrayList<>();
    SupportabilityMetrics metrics =
        new SupportabilityMetrics(
            Config.create(Collections.singletonMap("otel.javaagent.debug", "false")), reports::add);

    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.SERVER, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.INTERNAL, "otherInstrumentation");

    metrics.report();

    assertThat(reports).isEmpty();
  }

  @Test
  void reportsMetrics() {
    List<String> reports = new ArrayList<>();
    SupportabilityMetrics metrics =
        new SupportabilityMetrics(
            Config.create(Collections.singletonMap("otel.javaagent.debug", "true")), reports::add);

    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.SERVER, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");
    metrics.recordSuppressedSpan(SpanKind.INTERNAL, "otherInstrumentation");

    metrics.report();

    assertThat(reports)
        .isNotEmpty()
        .hasSize(3)
        .hasSameElementsAs(
            Arrays.asList(
                "Suppressed Spans by 'favoriteInstrumentation' (CLIENT) : 2",
                "Suppressed Spans by 'favoriteInstrumentation' (SERVER) : 1",
                "Suppressed Spans by 'otherInstrumentation' (INTERNAL) : 1"));
  }

  @Test
  void resetsCountsEachReport() {
    List<String> reports = new ArrayList<>();
    SupportabilityMetrics metrics =
        new SupportabilityMetrics(
            Config.create(Collections.singletonMap("otel.javaagent.debug", "true")), reports::add);

    metrics.recordSuppressedSpan(SpanKind.CLIENT, "favoriteInstrumentation");

    metrics.report();
    metrics.report();

    assertThat(reports)
        .isNotEmpty()
        .hasSize(1)
        .hasSameElementsAs(
            singletonList("Suppressed Spans by 'favoriteInstrumentation' (CLIENT) : 1"));
  }
}
