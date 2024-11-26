/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_GC_ACTION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_GC_NAME;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.END_OF_MAJOR_GC;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.END_OF_MINOR_GC;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SerialGcMemoryMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.GC_DURATION_METRICS));

  @Test
  void shouldHaveMemoryMetrics() {
    // TODO: needs JFR support. Placeholder.
  }

  @Test
  void shouldHaveGcDurationMetrics() {
    // TODO: Need a reliable way to test old and young gen GC in isolation.
    // Generate some JFR events
    System.gc();
    Attributes minorGcAttributes =
        Attributes.of(ATTR_GC_NAME, "Copy", ATTR_GC_ACTION, END_OF_MINOR_GC);
    Attributes majorGcAttributes =
        Attributes.of(ATTR_GC_NAME, "MarkSweepCompact", ATTR_GC_ACTION, END_OF_MAJOR_GC);
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_GC_DURATION)
                .hasUnit(SECONDS)
                .hasDescription(METRIC_DESCRIPTION_GC_DURATION)
                .satisfies(
                    data ->
                        assertThat(data.getHistogramData().getPoints())
                            .anyMatch(
                                p ->
                                    p.getSum() > 0
                                        && (p.getAttributes().equals(minorGcAttributes)
                                            || p.getAttributes().equals(majorGcAttributes)))));
  }
}
