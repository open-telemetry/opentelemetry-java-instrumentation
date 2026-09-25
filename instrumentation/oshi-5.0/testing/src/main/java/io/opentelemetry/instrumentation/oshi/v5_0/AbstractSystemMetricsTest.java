/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DiskIncubatingAttributes.DISK_IO_DIRECTION;
import static io.opentelemetry.semconv.incubating.NetworkIncubatingAttributes.NETWORK_INTERFACE_NAME;
import static io.opentelemetry.semconv.incubating.NetworkIncubatingAttributes.NETWORK_IO_DIRECTION;
import static io.opentelemetry.semconv.incubating.OtherIncubatingAttributes.STATE;
import static io.opentelemetry.semconv.incubating.SystemIncubatingAttributes.SYSTEM_DEVICE;
import static io.opentelemetry.semconv.incubating.SystemIncubatingAttributes.SYSTEM_MEMORY_STATE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public abstract class AbstractSystemMetricsTest {
  protected abstract void registerMetrics();

  protected abstract InstrumentationExtension testing();

  /**
   * @deprecated Exists only so the javaagent test can pin the pre-rename {@code
   *     io.opentelemetry.oshi} scope; to be removed in 3.0 once v3-preview becomes the default.
   */
  @Deprecated
  protected abstract String scopeName();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv and scopeName() bridge
  void memoryMetrics() {
    registerMetrics();

    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.memory.usage",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription(
                                v3Preview()
                                    ? "Reports memory in use by state."
                                    : "System memory usage")
                            .hasUnit("By")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasAttributesSatisfyingExactly(
                                                        equalTo(
                                                            v3Preview()
                                                                ? SYSTEM_MEMORY_STATE
                                                                : STATE,
                                                            "used"))
                                                    .hasValueSatisfying(v -> v.isNotNegative()),
                                            point ->
                                                point
                                                    .hasAttributesSatisfyingExactly(
                                                        equalTo(
                                                            v3Preview()
                                                                ? SYSTEM_MEMORY_STATE
                                                                : STATE,
                                                            "free"))
                                                    .hasValueSatisfying(v -> v.isNotNegative())))));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.memory.utilization",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription(
                                v3Preview()
                                    ? "Percentage of memory bytes in use."
                                    : "System memory utilization")
                            .hasUnit("1")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        v3Preview() ? SYSTEM_MEMORY_STATE : STATE,
                                                        "used"))
                                                .hasValueSatisfying(v -> v.isNotNegative()),
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        v3Preview() ? SYSTEM_MEMORY_STATE : STATE,
                                                        "free"))
                                                .hasValueSatisfying(v -> v.isNotNegative())))));
  }

  @Test
  @SuppressWarnings("deprecation") // using the legacy scopeName() bridge
  void networkAndDiskMetrics() {
    registerMetrics();

    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription(
                              v3Preview()
                                  ? "The number of bytes transmitted and received."
                                  : "System network IO")
                          .hasUnit("By")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertNetworkPoints(
                          metric.getLongSumData().getPoints(),
                          v3Preview() ? NETWORK_INTERFACE_NAME : stringKey("device"));
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            v3Preview() ? "system.network.packet.count" : "system.network.packets",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription(
                              v3Preview()
                                  ? "The number of packets transferred."
                                  : "System network packets")
                          .hasUnit(v3Preview() ? "{packet}" : "{packets}")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertNetworkPoints(
                          metric.getLongSumData().getPoints(),
                          v3Preview() ? SYSTEM_DEVICE : stringKey("device"));
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.errors",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription(
                              v3Preview()
                                  ? "Count of network errors detected."
                                  : "System network errors")
                          .hasUnit(v3Preview() ? "{error}" : "{errors}")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertNetworkPoints(
                          metric.getLongSumData().getPoints(),
                          v3Preview() ? NETWORK_INTERFACE_NAME : stringKey("device"));
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.disk.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription(
                              v3Preview() ? "Disk bytes transferred." : "System disk IO")
                          .hasUnit("By")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertDiskPoints(metric.getLongSumData().getPoints());
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.disk.operations",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription(
                              v3Preview() ? "Disk operations count." : "System disk operations")
                          .hasUnit(v3Preview() ? "{operation}" : "{operations}")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertDiskPoints(metric.getLongSumData().getPoints());
                    }));
  }

  @Test
  @SuppressWarnings("deprecation") // using the legacy scopeName() bridge
  void systemMetricsUseSystemSchema() {
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.memory.usage",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                          .isEqualTo(
                              v3Preview()
                                  ? SchemaUrls.V1_44_0
                                  : "https://opentelemetry.io/schemas/1.19.0");
                      assertThat(metric.getInstrumentationScopeInfo().getVersion())
                          .isEqualTo(
                              EmbeddedInstrumentationProperties.findVersion(
                                  "io.opentelemetry.oshi-5.0"));
                    }));
  }

  private static void assertNetworkPoints(
      Collection<LongPointData> points, AttributeKey<String> device) {
    assertThat(points)
        .allSatisfy(
            point -> {
              AttributeKey<String> direction =
                  v3Preview() ? NETWORK_IO_DIRECTION : stringKey("direction");
              assertThat(point.getAttributes().asMap()).containsOnlyKeys(device, direction);
              assertThat(point.getAttributes().get(device)).isNotBlank();
              assertThat(point.getAttributes().get(direction)).isIn("receive", "transmit");
            });
  }

  private static void assertDiskPoints(Collection<LongPointData> points) {
    assertThat(points)
        .allSatisfy(
            point -> {
              AttributeKey<String> device = v3Preview() ? SYSTEM_DEVICE : stringKey("device");
              AttributeKey<String> direction =
                  v3Preview() ? DISK_IO_DIRECTION : stringKey("direction");
              assertThat(point.getAttributes().asMap()).containsOnlyKeys(device, direction);
              assertThat(point.getAttributes().get(device)).isNotBlank();
              assertThat(point.getAttributes().get(direction)).isIn("read", "write");
            });
  }
}
