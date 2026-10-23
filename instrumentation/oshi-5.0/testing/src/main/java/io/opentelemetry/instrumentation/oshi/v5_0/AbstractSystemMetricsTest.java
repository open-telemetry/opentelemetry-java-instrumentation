/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeVersion;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DiskIncubatingAttributes.DISK_IO_DIRECTION;
import static io.opentelemetry.semconv.incubating.NetworkIncubatingAttributes.NETWORK_INTERFACE_NAME;
import static io.opentelemetry.semconv.incubating.NetworkIncubatingAttributes.NETWORK_IO_DIRECTION;
import static io.opentelemetry.semconv.incubating.SystemIncubatingAttributes.SYSTEM_DEVICE;
import static io.opentelemetry.semconv.incubating.SystemIncubatingAttributes.SYSTEM_MEMORY_STATE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public abstract class AbstractSystemMetricsTest {
  protected abstract void registerMetrics();

  protected abstract InstrumentationExtension testing();

  protected static String scopeName() {
    return "io.opentelemetry.oshi-5.0";
  }

  @Test
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
                            .hasDescription("Reports memory in use by state.")
                            .hasUnit("By")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point
                                                    .hasAttributesSatisfyingExactly(
                                                        equalTo(SYSTEM_MEMORY_STATE, "used"))
                                                    .hasValueSatisfying(v -> v.isNotNegative()),
                                            point ->
                                                point
                                                    .hasAttributesSatisfyingExactly(
                                                        equalTo(SYSTEM_MEMORY_STATE, "free"))
                                                    .hasValueSatisfying(v -> v.isNotNegative())))));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.memory.utilization",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("Percentage of memory bytes in use.")
                            .hasUnit("1")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(SYSTEM_MEMORY_STATE, "used"))
                                                .hasValueSatisfying(v -> v.isNotNegative()),
                                        point ->
                                            point
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(SYSTEM_MEMORY_STATE, "free"))
                                                .hasValueSatisfying(v -> v.isNotNegative())))));
  }

  @Test
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
                          .hasDescription("The number of bytes transmitted and received.")
                          .hasUnit("By")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertNetworkPoints(
                          metric.getLongSumData().getPoints(), NETWORK_INTERFACE_NAME);
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.packet.count",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription("The number of packets transferred.")
                          .hasUnit("{packet}")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertNetworkPoints(metric.getLongSumData().getPoints(), SYSTEM_DEVICE);
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.network.errors",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription("Count of network errors detected.")
                          .hasUnit("{error}")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertNetworkPoints(
                          metric.getLongSumData().getPoints(), NETWORK_INTERFACE_NAME);
                    }));
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.disk.io",
            metrics ->
                metrics.anySatisfy(
                    metric -> {
                      assertThat(metric)
                          .hasDescription("Disk bytes transferred.")
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
                          .hasDescription("Disk operations count.")
                          .hasUnit("{operation}")
                          .hasLongSumSatisfying(sum -> sum.isMonotonic());
                      assertDiskPoints(metric.getLongSumData().getPoints());
                    }));
  }

  @Test
  void systemMetricsUseSystemSchema() {
    String version = EmbeddedInstrumentationProperties.findVersion("io.opentelemetry.oshi-5.0");
    assertThat(version).isNotBlank();
    testing()
        .waitAndAssertMetrics(
            scopeName(),
            "system.memory.usage",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                            .satisfies(hasScopeVersion(version))));
  }

  @Test
  void metricIdentities() {
    testing()
        .waitAndAssertMetrics(
            scopeName(), "system.network.packet.count", metrics -> metrics.isNotEmpty());
    assertThat(testing().metrics())
        .filteredOn(metric -> metric.getInstrumentationScopeInfo().getName().equals(scopeName()))
        .allSatisfy(
            metric ->
                assertThat(metric)
                    .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                    .satisfies(
                        hasScopeVersion(
                            EmbeddedInstrumentationProperties.findVersion(scopeName()))))
        .extracting(MetricData::getName)
        .containsOnly(
            "system.memory.usage",
            "system.memory.utilization",
            "system.network.io",
            "system.network.packet.count",
            "system.network.errors",
            "system.disk.io",
            "system.disk.operations");
  }

  private static void assertNetworkPoints(
      Collection<LongPointData> points, AttributeKey<String> device) {
    assertThat(points)
        .allSatisfy(
            point -> {
              assertThat(point.getAttributes().asMap())
                  .containsOnlyKeys(device, NETWORK_IO_DIRECTION);
              assertThat(point.getAttributes().get(device)).isNotBlank();
              assertThat(point.getAttributes().get(NETWORK_IO_DIRECTION))
                  .isIn("receive", "transmit");
            });
  }

  private static void assertDiskPoints(Collection<LongPointData> points) {
    assertThat(points)
        .allSatisfy(
            point -> {
              assertThat(point.getAttributes().asMap())
                  .containsOnlyKeys(SYSTEM_DEVICE, DISK_IO_DIRECTION);
              assertThat(point.getAttributes().get(SYSTEM_DEVICE)).isNotBlank();
              assertThat(point.getAttributes().get(DISK_IO_DIRECTION)).isIn("read", "write");
            });
  }
}
