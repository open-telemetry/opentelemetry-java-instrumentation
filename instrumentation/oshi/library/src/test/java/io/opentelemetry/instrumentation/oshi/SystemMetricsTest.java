/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.sdk.metrics.data.MetricData.Type;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.Test;

public class SystemMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() throws Exception {
    SystemMetrics.registerObservers();
    IntervalMetricReader intervalMetricReader = createIntervalMetricReader();

    testMetricExporter.waitForData();
    intervalMetricReader.shutdown();

    verify("system.memory.usage", "By", Type.LONG_SUM, true);
    verify("system.memory.utilization", "1", Type.DOUBLE_GAUGE, true);

    verify("system.network.io", "By", Type.LONG_SUM, false);
    verify("system.network.packets", "packets", Type.LONG_SUM, false);
    verify("system.network.errors", "errors", Type.LONG_SUM, false);

    verify("system.disk.io", "By", Type.LONG_SUM, false);
    verify("system.disk.operations", "operations", Type.LONG_SUM, false);
  }
}
