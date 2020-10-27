/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import io.opentelemetry.sdk.metrics.data.MetricData.Type;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.Test;

public class ProcessMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() throws Exception {
    ProcessMetrics.registerObservers();
    IntervalMetricReader intervalMetricReader = createIntervalMetricReader();

    testMetricExporter.waitForData();
    intervalMetricReader.shutdown();

    verify("runtime.java.memory", "bytes", Type.NON_MONOTONIC_LONG, true);
    verify("runtime.java.cpu_time", "seconds", Type.SUMMARY, true);
    verify("runtime.java.gc_count", "counts", Type.SUMMARY, true);
  }
}
