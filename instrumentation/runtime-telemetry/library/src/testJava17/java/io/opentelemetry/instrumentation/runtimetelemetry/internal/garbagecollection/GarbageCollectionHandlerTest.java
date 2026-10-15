/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_ACTION;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_NAME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GarbageCollectionHandlerTest {

  @Test
  void recordsDurationWithCollectorName() throws InterruptedException {
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    try (SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {
      GarbageCollectionHandler handler = new GarbageCollectionHandler(meterProvider.get("test"));
      CountDownLatch recorded = new CountDownLatch(1);
      try (RecordingStream stream = new RecordingStream()) {
        stream.enable(handler.getEventName());
        stream.onEvent(
            handler.getEventName(),
            event -> {
              handler.accept(event);
              recorded.countDown();
            });
        stream.startAsync();
        await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () -> {
                  System.gc();
                  return recorded.await(100, MILLISECONDS);
                });
      }

      MetricData metric = reader.collectAllMetrics().iterator().next();
      assertThat(metric).hasName("jvm.gc.duration").hasUnit("s");
      assertThat(metric.getHistogramData().getPoints())
          .isNotEmpty()
          .allSatisfy(
              point -> {
                assertThat(point.getSum()).isPositive();
                assertThat(point.getAttributes().get(JVM_GC_NAME)).isNotBlank();
                assertThat(point.getAttributes().get(JVM_GC_ACTION)).isNull();
              });
    }
  }
}
