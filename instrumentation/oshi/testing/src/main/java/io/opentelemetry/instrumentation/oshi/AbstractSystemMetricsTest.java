/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

public abstract class AbstractSystemMetricsTest {

  private static final AttributeKey<String> STATE = AttributeKey.stringKey("state");

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
            "system.memory.usage",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("By")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributesSatisfying(equalTo(STATE, "used"))
                                                .hasValueSatisfying(v -> v.isPositive()),
                                        point ->
                                            point
                                                .hasAttributesSatisfying(equalTo(STATE, "free"))
                                                .hasValueSatisfying(v -> v.isPositive())))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.memory.utilization",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("1")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributesSatisfying(equalTo(STATE, "used"))
                                                .hasValueSatisfying(v -> v.isPositive()),
                                        point ->
                                            point
                                                .hasAttributesSatisfying(equalTo(STATE, "free"))
                                                .hasValueSatisfying(v -> v.isPositive())))));
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
                        assertThat(metric).hasUnit("{packets}").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "system.network.errors",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric).hasUnit("{errors}").hasLongSumSatisfying(sum -> {})));
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
                        assertThat(metric)
                            .hasUnit("{operations}")
                            .hasLongSumSatisfying(sum -> {})));
  }
}
