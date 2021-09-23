/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.Test;

public class ProcessMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() throws Exception {
    ProcessMetrics.registerObservers();
    IntervalMetricReader intervalMetricReader = createIntervalMetricReader();

    testMetricExporter.waitForData();
    intervalMetricReader.shutdown();

    verify(
        "runtime.java.memory", "bytes", MetricDataType.LONG_GAUGE, /* checkNonZeroValue= */ true);
    verify(
        "runtime.java.cpu_time",
        "seconds",
        MetricDataType.DOUBLE_GAUGE,
        /* checkNonZeroValue= */ true);
  }
}
