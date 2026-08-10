/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DescriptionDeduplicationTest {

  @Test
  void descriptionsAreNotSharedAcrossRegistries() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();

    try (SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {
      OpenTelemetrySdk openTelemetry =
          OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();

      MeterRegistry firstRegistry = OpenTelemetryMeterRegistry.builder(openTelemetry).build();
      MeterRegistry secondRegistry = OpenTelemetryMeterRegistry.builder(openTelemetry).build();

      Counter.builder("testCounter")
          .description("First description")
          .register(firstRegistry)
          .increment();
      Counter.builder("testCounter")
          .description("Second description")
          .register(secondRegistry)
          .increment();

      assertThat(reader.collectAllMetrics())
          .extracting(MetricData::getDescription)
          .containsExactlyInAnyOrder("First description", "Second description");
    }
  }

  @Test
  void descriptionsAreDeduplicatedOnTheSuffixedInstrumentName() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    AtomicLong count = new AtomicLong(1);

    try (SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {

      MeterRegistry registry =
          OpenTelemetryMeterRegistry.builder(
                  OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build())
              .build();

      // emits testSuffix.sum as an async double counter with unit s, colliding with the function
      // counter below
      FunctionTimer.builder("testSuffix", count, AtomicLong::get, AtomicLong::doubleValue, SECONDS)
          .description("First description")
          .register(registry);
      FunctionCounter.builder("testSuffix.sum", count, AtomicLong::doubleValue)
          .description("Second description")
          .baseUnit("s")
          .register(registry);

      assertThat(reader.collectAllMetrics())
          .filteredOn(metric -> metric.getName().equals("testSuffix.sum"))
          .singleElement()
          .extracting(MetricData::getDescription)
          .isEqualTo("First description");
    }
  }
}
