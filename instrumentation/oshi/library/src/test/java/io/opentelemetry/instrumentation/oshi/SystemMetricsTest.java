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

    verify("system.memory.usage", "bytes", Type.NON_MONOTONIC_LONG, true);
    verify("system.memory.utilization", "1", Type.GAUGE_DOUBLE, true);

    verify("system.network.io", "bytes", Type.MONOTONIC_LONG, false);
    verify("system.network.packets", "packets", Type.MONOTONIC_LONG, false);
    verify("system.network.errors", "errors", Type.MONOTONIC_LONG, false);

    verify("system.disk.io", "bytes", Type.MONOTONIC_LONG, false);
    verify("system.disk.operations", "operations", Type.MONOTONIC_LONG, false);
  }
}
