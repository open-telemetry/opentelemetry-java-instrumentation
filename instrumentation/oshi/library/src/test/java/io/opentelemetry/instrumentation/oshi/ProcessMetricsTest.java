/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;

import org.junit.jupiter.api.Test;

public class ProcessMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() {
    ProcessMetrics.registerObservers();

    waitAndAssertMetrics(
        metric ->
            metric
                .hasName("runtime.java.memory")
                .hasUnit("bytes")
                .hasLongSum()
                .points()
                .anySatisfy(point -> assertThat(point.getValue()).isPositive()),
        metric ->
            metric
                .hasName("runtime.java.cpu_time")
                .hasUnit("seconds")
                .hasDoubleGauge()
                .points()
                .anySatisfy(point -> assertThat(point.getValue()).isPositive()));
  }
}
