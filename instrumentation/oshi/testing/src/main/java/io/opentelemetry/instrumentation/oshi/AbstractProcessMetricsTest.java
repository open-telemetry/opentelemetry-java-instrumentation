/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

public abstract class AbstractProcessMetricsTest {

  protected abstract void registerMetrics();

  protected abstract InstrumentationExtension testing();

  @Test
  void test() {
    // when
    registerMetrics();

    // then
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "runtime.java.memory",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("By")
                            // TODO(anuraaga): Provide fuzzy value matching
                            .hasLongSumSatisfying(
                                sum ->
                                    assertThat(metric.getLongSumData().getPoints())
                                        .anySatisfy(
                                            point -> assertThat(point.getValue()).isPositive()))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "runtime.java.cpu_time",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            // TODO(anuraaga): Provide fuzzy value matching
                            .hasLongGaugeSatisfying(
                                gauge ->
                                    assertThat(metric.getLongGaugeData().getPoints())
                                        .anySatisfy(
                                            point -> assertThat(point.getValue()).isPositive()))));
  }
}
