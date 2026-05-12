/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

public abstract class AbstractProcessMetricsTest {

  protected abstract void registerMetrics();

  protected abstract InstrumentationExtension testing();

  @Test
  @EnabledIfSystemProperty(named = "testExperimental", matches = "true")
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
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("type"), "rss"))
                                                .hasValueSatisfying(v -> v.isPositive()),
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("type"), "vms"))
                                                .hasValueSatisfying(v -> v.isPositive())))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.oshi",
            "runtime.java.cpu_time",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasLongGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("type"), "user"))
                                                .hasValueSatisfying(v -> v.isNotNegative()),
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey("type"), "system"))
                                                .hasValueSatisfying(v -> v.isNotNegative())))));
  }
}
