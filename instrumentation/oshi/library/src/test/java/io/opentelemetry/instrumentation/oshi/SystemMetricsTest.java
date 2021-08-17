/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.Test;

public class SystemMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() throws Exception {
    SystemMetrics.registerObservers();
    IntervalMetricReader intervalMetricReader = createIntervalMetricReader();

    testMetricExporter.waitForData();
    intervalMetricReader.shutdown();

    verify("system.memory.usage", "By", MetricDataType.LONG_GAUGE, /* checkNonZeroValue= */ true);
    verify(
        "system.memory.utilization",
        "1",
        MetricDataType.DOUBLE_GAUGE,
        /* checkNonZeroValue= */ true);

    verify("system.network.io", "By", MetricDataType.LONG_GAUGE, /* checkNonZeroValue= */ false);
    verify(
        "system.network.packets",
        "packets",
        MetricDataType.LONG_GAUGE,
        /* checkNonZeroValue= */ false);
    verify(
        "system.network.errors",
        "errors",
        MetricDataType.LONG_GAUGE,
        /* checkNonZeroValue= */ false);

    verify("system.disk.io", "By", MetricDataType.LONG_GAUGE, /* checkNonZeroValue= */ false);
    verify(
        "system.disk.operations",
        "operations",
        MetricDataType.LONG_GAUGE,
        /* checkNonZeroValue= */ false);
  }
}
