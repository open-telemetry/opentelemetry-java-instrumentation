/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;

import org.junit.jupiter.api.Test;

public class SystemMetricsTest extends AbstractMetricsTest {

  @Test
  public void test() {
    SystemMetrics.registerObservers();

    waitAndAssertMetrics(
        metric ->
            metric
                .hasName("system.memory.usage")
                .hasUnit("By")
                .hasLongSum()
                .points()
                .anySatisfy(point -> assertThat(point.getValue()).isPositive()),
        metric ->
            metric
                .hasName("system.memory.utilization")
                .hasUnit("1")
                .hasDoubleGauge()
                .points()
                .anySatisfy(point -> assertThat(point.getValue()).isPositive()),
        metric -> metric.hasName("system.network.io").hasUnit("By").hasLongSum(),
        metric -> metric.hasName("system.network.packets").hasUnit("packets").hasLongSum(),
        metric -> metric.hasName("system.network.errors").hasUnit("errors").hasLongSum(),
        metric -> metric.hasName("system.disk.operations").hasUnit("operations").hasLongSum());
  }
}
