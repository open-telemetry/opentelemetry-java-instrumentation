/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

class DescriptionDeduplicationTest {

  @Test
  void descriptionsAreNotSharedAcrossMeterProviders() {
    InMemoryMetricReader firstReader = InMemoryMetricReader.create();
    InMemoryMetricReader secondReader = InMemoryMetricReader.create();

    try (SdkMeterProvider firstMeterProvider =
            SdkMeterProvider.builder().registerMetricReader(firstReader).build();
        SdkMeterProvider secondMeterProvider =
            SdkMeterProvider.builder().registerMetricReader(secondReader).build()) {

      MeterRegistry firstRegistry =
          OpenTelemetryMeterRegistry.builder(
                  OpenTelemetrySdk.builder().setMeterProvider(firstMeterProvider).build())
              .build();
      MeterRegistry secondRegistry =
          OpenTelemetryMeterRegistry.builder(
                  OpenTelemetrySdk.builder().setMeterProvider(secondMeterProvider).build())
              .build();

      Counter.builder("testCounter")
          .description("First description")
          .register(firstRegistry)
          .increment();
      Counter.builder("testCounter")
          .description("Second description")
          .register(secondRegistry)
          .increment();

      assertThat(firstReader.collectAllMetrics())
          .singleElement()
          .extracting(MetricData::getDescription)
          .isEqualTo("First description");
      assertThat(secondReader.collectAllMetrics())
          .singleElement()
          .extracting(MetricData::getDescription)
          .isEqualTo("Second description");
    }
  }
}
