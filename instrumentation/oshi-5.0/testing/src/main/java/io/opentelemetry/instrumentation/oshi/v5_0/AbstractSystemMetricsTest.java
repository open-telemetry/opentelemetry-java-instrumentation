/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // uses the deprecated scopeName() bridge
public abstract class AbstractSystemMetricsTest {

  private static final AttributeKey<String> STATE = AttributeKey.stringKey("state");

  protected abstract void registerMetrics();

  protected abstract InstrumentationExtension testing();

  /**
   * @deprecated Exists only so the javaagent test can pin the pre-rename {@code
   *     io.opentelemetry.oshi} scope; to be removed in 3.0 once v3-preview becomes the default.
   */
  @Deprecated
  protected abstract String scopeName();

  @Test
  void test() {
    // when
    registerMetrics();

    // then
    testing()
        .waitAndAssertMetrics(
            scopeName(),
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(STATE, "used"))
                                                .hasValueSatisfying(v -> v.isNotNegative()),
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(STATE, "free"))
                                                .hasValueSatisfying(v -> v.isNotNegative())))));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
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
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(STATE, "used"))
                                                .hasValueSatisfying(v -> v.isNotNegative()),
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(STATE, "free"))
                                                .hasValueSatisfying(v -> v.isNotNegative())))));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> assertThat(metric).hasUnit("By").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.packets",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric).hasUnit("{packets}").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.errors",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric).hasUnit("{errors}").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.disk.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> assertThat(metric).hasUnit("By").hasLongSumSatisfying(sum -> {})));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.disk.operations",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("{operations}")
                            .hasLongSumSatisfying(sum -> {})));
  }
}
