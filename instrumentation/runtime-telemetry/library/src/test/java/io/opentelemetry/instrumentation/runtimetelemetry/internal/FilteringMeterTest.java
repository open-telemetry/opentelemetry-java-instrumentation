/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

class FilteringMeterTest {

  @Test
  void delegatesOnlyMatchingInstruments() throws Exception {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    Meter meter =
        new FilteringMeter(
            sdk.getMeter("test"), MetricNameFilter.create(singletonList("jvm.memory.*")));

    try (AutoCloseable memory =
            meter.gaugeBuilder("jvm.memory.used").buildWithCallback(m -> m.record(1));
        AutoCloseable cpu =
            meter.gaugeBuilder("jvm.cpu.time").buildWithCallback(m -> m.record(1))) {
      assertThat(
              reader.collectAllMetrics().stream().map(metric -> metric.getName()).collect(toSet()))
          .containsExactly("jvm.memory.used");
    } finally {
      sdk.close();
    }
  }
}
