/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilteringMeterTest {

  private static final IncludeExclude INCLUDE_EXCLUDE =
      IncludeExclude.builder().setExcluded("excluded*").build();

  private InMemoryMetricReader metricReader;
  private FilteringMeter meter;

  @BeforeEach
  void before() {
    metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    Meter sdkMeter = meterProvider.get("test");
    meter = new FilteringMeter(sdkMeter, INCLUDE_EXCLUDE);
  }

  @Test
  void counter() {
    meter.counterBuilder("excluded").build().add(1);
    assertThat(metricReader.collectAllMetrics()).isEmpty();

    meter.counterBuilder("included").build().add(1);
    checkReportedMetrics("included");
  }

  @Test
  void upDownCounter() {
    meter.upDownCounterBuilder("excluded").build().add(1);
    assertThat(metricReader.collectAllMetrics()).isEmpty();

    meter.upDownCounterBuilder("included").build().add(1);
    checkReportedMetrics("included");
  }

  @Test
  void histogram() {
    meter.histogramBuilder("excluded").build().record(1.0);
    assertThat(metricReader.collectAllMetrics()).isEmpty();

    meter.histogramBuilder("included").build().record(1.0);
    checkReportedMetrics("included");
  }

  @Test
  void gauge() {
    meter.gaugeBuilder("excluded").build().set(1.0);
    assertThat(metricReader.collectAllMetrics()).isEmpty();

    meter.gaugeBuilder("included").build().set(1.0);
    checkReportedMetrics("included");
  }

  @Test
  void batchCallbackFiltering() {
    ObservableLongMeasurement excludedObs = meter.counterBuilder("excluded").buildObserver();
    ObservableLongMeasurement included1 = meter.counterBuilder("included.first").buildObserver();
    ObservableLongMeasurement included2 = meter.counterBuilder("included.second").buildObserver();

    // all noop: callback not registered, nothing recorded
    try (BatchCallback bc = meter.batchCallback(() -> excludedObs.record(1), excludedObs)) {
      assertThat(metricReader.collectAllMetrics()).isEmpty();
    }

    // real only: callback registered, metric recorded
    try (BatchCallback bc = meter.batchCallback(() -> included1.record(1), included1)) {
      checkReportedMetrics("included.first");
    }

    // mixed noop + real: noop filtered out, real delegated
    try (BatchCallback bc =
        meter.batchCallback(
            () -> {
              excludedObs.record(1);
              included1.record(1);
              included2.record(1);
            },
            excludedObs,
            included1,
            included2)) {
      checkReportedMetrics("included.first", "included.second");
    }
  }

  private void checkReportedMetrics(String... names) {
    assertThat(metricReader.collectAllMetrics())
        .extracting(MetricData::getName)
        .containsExactlyInAnyOrder(names);
  }
}
