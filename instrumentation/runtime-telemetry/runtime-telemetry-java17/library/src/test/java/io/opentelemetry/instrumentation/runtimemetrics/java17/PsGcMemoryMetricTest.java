/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_GC_ACTION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_GC_NAME;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_PS_EDEN_SPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_PS_OLD_GEN;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_PS_SURVIVOR_SPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.END_OF_MAJOR_GC;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.END_OF_MINOR_GC;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_MEMORY_AFTER;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_MEMORY_LIMIT;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_MEMORY_AFTER;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_MEMORY_LIMIT;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.MetricData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PsGcMemoryMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder ->
              builder
                  .disableAllFeatures()
                  .enableFeature(JfrFeature.GC_DURATION_METRICS)
                  .enableFeature(JfrFeature.MEMORY_POOL_METRICS));

  @Test
  void shouldHaveMemoryMetrics() {
    System.gc();
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY)
                .satisfies(PsGcMemoryMetricTest::hasGcAttributes),
        metric ->
            metric
                .hasName(METRIC_NAME_COMMITTED)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_COMMITTED)
                .satisfies(PsGcMemoryMetricTest::hasGcAttributes),
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_LIMIT)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_LIMIT)
                .satisfies(PsGcMemoryMetricTest::hasGcAttributes),
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_AFTER)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_AFTER)
                .satisfies(PsGcMemoryMetricTest::hasGcAttributes));
  }

  private static void hasGcAttributes(MetricData data) {
    assertThat(data.getLongSumData().getPoints())
        .anyMatch(p -> p.getAttributes().equals(ATTR_PS_EDEN_SPACE))
        .anyMatch(p -> p.getAttributes().equals(ATTR_PS_SURVIVOR_SPACE))
        .anyMatch(p -> p.getAttributes().equals(ATTR_PS_OLD_GEN));
  }

  @Test
  void shouldHaveGcDurationMetrics() {
    // TODO: Need a reliable way to test old and young gen GC in isolation.
    // Generate some JFR events
    System.gc();

    Attributes minorGcAttributes =
        Attributes.of(ATTR_GC_NAME, "PS Scavenge", ATTR_GC_ACTION, END_OF_MINOR_GC);
    Attributes majorGcAttributes =
        Attributes.of(ATTR_GC_NAME, "PS MarkSweep", ATTR_GC_ACTION, END_OF_MAJOR_GC);
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
