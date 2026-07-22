/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricBridgeFilterTest {

  @Test
  void nullConfigDropsNothing() {
    MetricBridgeFilter filter = MetricBridgeFilter.create(null);
    assertThat(filter.shouldDrop("jvm.memory.used")).isFalse();
  }

  @Test
  void emptyOrWhitespaceConfigDropsNothing() {
    MetricBridgeFilter filterEmpty = MetricBridgeFilter.create("");
    assertThat(filterEmpty.shouldDrop("jvm.memory.used")).isFalse();

    MetricBridgeFilter filterWhitespace = MetricBridgeFilter.create("   ");
    assertThat(filterWhitespace.shouldDrop("jvm.memory.used")).isFalse();
  }

  @Test
  void matchesExactMetricNames() {
    MetricBridgeFilter filter = MetricBridgeFilter.create("process.cpu.usage,jvm.memory.used");

    assertThat(filter.shouldDrop("process.cpu.usage")).isTrue();
    assertThat(filter.shouldDrop("jvm.memory.used")).isTrue();
    assertThat(filter.shouldDrop("process.cpu.usage.time")).isFalse();
    assertThat(filter.shouldDrop("custom.business.metric")).isFalse();
  }

  @Test
  void matchesWildcardPrefixes() {
    MetricBridgeFilter filter = MetricBridgeFilter.create("jvm.*,system.*");

    assertThat(filter.shouldDrop("jvm.memory.used")).isTrue();
    assertThat(filter.shouldDrop("jvm.gc.pause")).isTrue();
    assertThat(filter.shouldDrop("system.cpu.load")).isTrue();
    assertThat(filter.shouldDrop("jvm")).isFalse();
    assertThat(filter.shouldDrop("custom.business.metric")).isFalse();
  }

  @Test
  void handlesMixedConfigurationsAndTrimsWhitespace() {
    MetricBridgeFilter filter =
        MetricBridgeFilter.create(" exact.metric , prefix.metric.* ,  another.exact  ");

    assertThat(filter.shouldDrop("exact.metric")).isTrue();
    assertThat(filter.shouldDrop("another.exact")).isTrue();
    assertThat(filter.shouldDrop("exact.metric.extra")).isFalse();
    assertThat(filter.shouldDrop("prefix.metric.one")).isTrue();
    assertThat(filter.shouldDrop("prefix.metric.two")).isTrue();
    assertThat(filter.shouldDrop("unmatched.metric")).isFalse();
  }
}
