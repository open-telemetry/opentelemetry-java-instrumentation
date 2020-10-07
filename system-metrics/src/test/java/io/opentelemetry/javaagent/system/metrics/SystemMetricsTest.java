/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.system.metrics;

import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.Test;

public class SystemMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() throws Exception {
    SystemMetrics.registerObservers();
    IntervalMetricReader intervalMetricReader = createIntervalMetricReader();

    testMetricExporter.waitForData();
    intervalMetricReader.shutdown();

    verify("system.memory.usage", true);
    verify("system.memory.utilization", true);

    verify("system.network.io", false);
    verify("system.network.packets", false);
    verify("system.network.errors", false);

    verify("system.disk.io", false);
    verify("system.disk.operations", false);
  }
}
