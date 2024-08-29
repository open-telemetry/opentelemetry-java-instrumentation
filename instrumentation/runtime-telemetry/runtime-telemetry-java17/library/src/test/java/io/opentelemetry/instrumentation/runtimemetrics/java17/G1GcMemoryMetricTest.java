/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_G1_EDEN_SPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_G1_SURVIVOR_SPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_GC_ACTION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_GC_NAME;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.END_OF_MAJOR_GC;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.END_OF_MINOR_GC;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_MEMORY_AFTER;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_GC_DURATION;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_MEMORY_AFTER;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.MetricData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class G1GcMemoryMetricTest {

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
    // Test to make sure there's metric data for both eden and survivor spaces.
    // TODO: once G1 old gen usage added to jdk.G1HeapSummary (in JDK 21), test for it here too.
    // TODO: needs JFR support for jvm.memory.limit.
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY)
                .satisfies(G1GcMemoryMetricTest::hasGcAttributes),
        metric ->
            metric
                .hasName(METRIC_NAME_COMMITTED)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_COMMITTED)
                // TODO: need JFR support for the other G1 pools
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getAttributes().equals(ATTR_G1_EDEN_SPACE))),
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_AFTER)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_AFTER)
                .satisfies(G1GcMemoryMetricTest::hasGcAttributes));
  }

  private static void hasGcAttributes(MetricData data) {
    assertThat(data.getLongSumData().getPoints())
        .anyMatch(p -> p.getAttributes().equals(ATTR_G1_EDEN_SPACE))
        .anyMatch(p -> p.getAttributes().equals(ATTR_G1_SURVIVOR_SPACE));
  }

  @Test
  void shouldHaveGcDurationMetrics() {
    // TODO: Need a reliable way to test old and young gen GC in isolation.
    System.gc();
    Attributes minorGcAttributes =
        Attributes.of(ATTR_GC_NAME, "G1 Young Generation", ATTR_GC_ACTION, END_OF_MINOR_GC);
    Attributes majorGcAttributes =
        Attributes.of(ATTR_GC_NAME, "G1 Old Generation", ATTR_GC_ACTION, END_OF_MAJOR_GC);
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
