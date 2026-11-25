/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_NAME;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.List;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledInNativeImage;

@EnabledInNativeImage
class NativeImageGcTest {

  @Test
  void recordsSerialGcDurationFromJfr() {
    assertThat(FlightRecorder.isAvailable()).isTrue();
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    try (SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {
      RuntimeTelemetryBuilder builder =
          RuntimeTelemetry.builder(
                  OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build())
              .disableAllJmx();
      Experimental.setJfrMetrics(
          builder, IncludeExclude.builder().setIncluded(List.of("jvm.gc.duration")).build());

      try (RuntimeTelemetry telemetry = builder.build()) {
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
              System.gc();
              assertThat(reader.collectAllMetrics())
                  .singleElement()
                  .satisfies(NativeImageGcTest::assertGcDuration);
            });
      } catch (ConditionTimeoutException e) {
        List<String> gcEventTypes =
            FlightRecorder.getFlightRecorder().getEventTypes().stream()
                .map(EventType::getName)
                .filter(name -> name.startsWith("jdk.GC") || name.contains("GarbageCollection"))
                .sorted()
                .toList();
        throw new AssertionError(
            "Timed out waiting for jvm.gc.duration. Available JFR GC event types: " + gcEventTypes,
            e);
      }
    }
  }

  private static void assertGcDuration(MetricData metric) {
    assertThat(metric).hasName("jvm.gc.duration").hasUnit("s");
    assertThat(metric.getType()).isEqualTo(MetricDataType.HISTOGRAM);
    assertThat(metric.getHistogramData().getPoints())
        .singleElement()
        .satisfies(point -> {
          assertThat(point.getCount()).isPositive();
          assertThat(point.getAttributes()).isEqualTo(Attributes.of(JVM_GC_NAME, "serial"));
        });
  }
}
