/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.system.metrics;

import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.Test;

public class JavaMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() throws Exception {
    JavaMetrics.registerObservers();
    IntervalMetricReader intervalMetricReader = createIntervalMetricReader();

    testMetricExporter.waitForData();
    intervalMetricReader.shutdown();

    verify("runtime.java.memory", true);
    verify("runtime.java.cpu_time", true);
    verify("runtime.java.gc_count", true);
  }
}
