/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

public abstract class AbstractSystemMetricsTest {

  protected abstract void registerMetrics();

  protected abstract InstrumentationExtension testing();

  @Test
  public void test() {
    // when
    registerMetrics();

    // then
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.memory.usage",
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
            "system.memory.utilization",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("1")
                            // TODO(anuraaga): Provide fuzzy value matching
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    assertThat(metric.getDoubleGaugeData().getPoints())
                                        .anySatisfy(
                                            point -> assertThat(point.getValue()).isPositive()))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.network.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> assertThat(metric).hasUnit("By").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.network.packets",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric).hasUnit("packets").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.network.errors",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric).hasUnit("errors").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.disk.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> assertThat(metric).hasUnit("By").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.disk.operations",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric).hasUnit("operations").hasLongSumSatisfying(sum -> {})));
  }
}
