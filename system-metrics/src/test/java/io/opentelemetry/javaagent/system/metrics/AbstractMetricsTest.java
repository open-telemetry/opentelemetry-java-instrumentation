/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.system.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.Point;
import io.opentelemetry.sdk.metrics.data.MetricData.SummaryPoint;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

public class AbstractMetricsTest {
  TestMetricExporter testMetricExporter;

  @BeforeEach
  public void beforeEach() {
    testMetricExporter = new TestMetricExporter();
  }

  IntervalMetricReader createIntervalMetricReader() {
    return IntervalMetricReader.builder()
        .setExportIntervalMillis(100)
        .setMetricExporter(testMetricExporter)
        .setMetricProducers(
            Collections.singletonList(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
        .build();
  }

  public void verify(String metricName, boolean checkValue) {
    List<MetricData> metricDataList = testMetricExporter.metricDataList;
    for (MetricData metricData : metricDataList) {
      if (metricData.getName().equals(metricName)) {
        assertThat(metricData.getDescription()).isNotEmpty();
        assertThat(metricData.getUnit()).isNotEmpty();
        assertThat(metricData.getPoints()).isNotEmpty();
        if (checkValue) {
          for (Point point : metricData.getPoints()) {
            SummaryPoint summaryPoint = (SummaryPoint) point;
            assertThat(summaryPoint.getSum()).isGreaterThan(0.0);
          }
        }
        return;
      }
    }
    Assertions.fail("No metric for " + metricName);
  }

  static class TestMetricExporter implements MetricExporter {
    private final List<MetricData> metricDataList = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public CompletableResultCode export(Collection<MetricData> collection) {
      metricDataList.addAll(collection);
      latch.countDown();
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public void shutdown() {}

    public void waitForData() throws InterruptedException {
      latch.await(1, TimeUnit.SECONDS);
    }
  }
}
