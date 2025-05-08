/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.metrics.MetricRecord;
import software.amazon.awssdk.metrics.SdkMetric;
import software.amazon.awssdk.metrics.internal.DefaultMetricCollection;
import software.amazon.awssdk.metrics.internal.DefaultMetricRecord;

class OpenTelemetryMetricPublisherTest {

  private ExecutorService executor;
  private InMemoryMetricReader metricReader;
  private MetricPublisher metricPublisher;
  private static final String basePrefix = "custom.prefix";

  @BeforeEach
  void setUp() {
    // Create an executor for the OpenTelemetryMetricPublisher
    executor = Executors.newSingleThreadExecutor();

    // Set up an InMemoryMetricReader to capture metrics
    metricReader = InMemoryMetricReader.create();

    // Setup some base attributes
    Attributes baseAttributes =
        Attributes.of(
            AttributeKey.stringKey("custom.dimension.key.1"),
            "CustomDimensionValue.1",
            AttributeKey.stringKey("custom.dimension.key.2"),
            "CustomDimensionValue.2");

    // Set up the SdkMeterProvider with the metric reader
    SdkMeterProvider sdkMeterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();

    // Set up OpenTelemetry with the SdkMeterProvider
    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder().setMeterProvider(sdkMeterProvider).build();

    GlobalOpenTelemetry.resetForTest();
    GlobalOpenTelemetry.set(openTelemetrySdk);

    // Create an instance of OpenTelemetryMetricPublisher
    metricPublisher =
        new OpenTelemetryMetricPublisher(
            GlobalOpenTelemetry.get(), basePrefix, executor, baseAttributes);
  }

  @AfterEach
  void tearDown() {
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  public void testPublishMetrics() throws InterruptedException {
    // Create a mock MetricCollection
    MetricCollection metricCollection = createMockMetricCollection();

    // Publish the metrics
    metricPublisher.publish(metricCollection);

    // Wait for executor to process the metrics
    executor.execute(() -> {}); // Submit a no-op task to ensure previous tasks are completed
    Thread.sleep(500); // Wait briefly for tasks to complete

    // Retrieve the recorded metrics
    List<MetricData> exportedMetrics = new ArrayList<>(metricReader.collectAllMetrics());

    // Verify that the expected metrics are recorded
    assertEquals(1, exportedMetrics.size(), "Expected one metric to be exported");

    MetricData metricData = exportedMetrics.get(0);
    assertEquals(basePrefix + ".api_call_duration", metricData.getName());
    assertEquals(
        "The total time taken to finish a request (inclusive of all retries)",
        metricData.getDescription());
    assertEquals("ns", metricData.getUnit());

    // Verify the data points
    HistogramData histogramData = metricData.getHistogramData();
    Collection<HistogramPointData> points = histogramData.getPoints();
    assertEquals(1, points.size(), "Expected one data point");

    HistogramPointData point = points.iterator().next();
    assertEquals(100.0, point.getSum(), 0.001, "Expected sum to be 100.0");
    assertEquals(1, point.getCount(), "Expected count to be 1");
    assertEquals(
        "GetItem", point.getAttributes().get(AttributeKey.stringKey("request_operation_name")));
    assertEquals(true, point.getAttributes().get(AttributeKey.booleanKey("request_is_success")));
    assertEquals(0L, point.getAttributes().get(AttributeKey.longKey("request_retry_count")));
    assertEquals(
        "CustomDimensionValue.1",
        point.getAttributes().get(AttributeKey.stringKey("custom.dimension.key.1")));
    assertEquals(
        "CustomDimensionValue.2",
        point.getAttributes().get(AttributeKey.stringKey("custom.dimension.key.2")));
  }

  private static MetricCollection createMockMetricCollection() {
    // Create a Map to hold the metrics
    Map<SdkMetric<?>, List<MetricRecord<?>>> metrics = new HashMap<>();

    // For API_CALL_DURATION
    MetricRecord<Duration> apiCallDurationRecord =
        new DefaultMetricRecord<>(CoreMetric.API_CALL_DURATION, Duration.ofNanos(100));
    metrics.put(CoreMetric.API_CALL_DURATION, Collections.singletonList(apiCallDurationRecord));

    // For OPERATION_NAME
    MetricRecord<String> operationNameRecord =
        new DefaultMetricRecord<>(CoreMetric.OPERATION_NAME, "GetItem");
    metrics.put(CoreMetric.OPERATION_NAME, Collections.singletonList(operationNameRecord));

    // For API_CALL_SUCCESSFUL
    MetricRecord<Boolean> apiCallSuccessfulRecord =
        new DefaultMetricRecord<>(CoreMetric.API_CALL_SUCCESSFUL, true);
    metrics.put(CoreMetric.API_CALL_SUCCESSFUL, Collections.singletonList(apiCallSuccessfulRecord));

    // For RETRY_COUNT
    MetricRecord<Integer> retryCountRecord = new DefaultMetricRecord<>(CoreMetric.RETRY_COUNT, 0);
    metrics.put(CoreMetric.RETRY_COUNT, Collections.singletonList(retryCountRecord));

    // Create the MetricCollection using DefaultMetricCollection
    return new DefaultMetricCollection("ApiCall", metrics, Collections.emptyList());
  }
} 
